"""Component-only tests: deterministic workflow, provider boundary and database API."""

import json
import os
import unittest
import uuid
from unittest.mock import AsyncMock, patch

from langchain_core.messages import AIMessage
from pydantic import ValidationError
from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import async_sessionmaker
from test_trips import http

from app.config import get_settings
from app.db import create_engine
from app.main import create_app
from app.planning.assistant import interpret, options_for
from app.planning.contracts import (
    STEPS,
    Decision,
    Option,
    Proposal,
    SessionState,
    StepId,
    Turn,
)
from app.planning.logic import (
    PlanningError,
    apply_decision,
    confirm,
    fingerprint,
    generation_input,
    readiness,
)
from app.planning.models import PlanningSession, PlanningTurn
from app.planning.routes import database


def location(label="Sydney, Australia", latitude=-33.8, longitude=151.2):
    return dict(
        label=label,
        countryCode="AU",
        latitude=latitude,
        longitude=longitude,
        timeZone="Australia/Sydney",
        provider="OPEN_METEO",
    )


def answers():
    return [
        location(),
        location("Osaka, Japan", 34.7, 135.5),
        dict(mode="FIXED", startDate="2026-12-10", endDate="2026-12-14"),
        dict(adults=2, childrenAges=[], description="With my partner"),
        dict(
            description="AUD 5000 total",
            currency="AUD",
            amountMinor=500000,
            scope="ENTIRE_PARTY_WHOLE_TRIP",
            includesFlights=True,
        ),
        dict(requirements=[dict(description="Less walking", strength="PREFERRED")]),
        dict(pace="RELAXED", description="Regular breaks"),
        dict(preferences=["Food", "Culture"]),
    ]


def complete():
    state = SessionState()
    for step, answer in zip(STEPS, answers(), strict=True):
        state.steps[step].state, state.steps[step].answer = "CONFIRMED", answer
    state.currentStep = StepId.FOLLOW_UP_2
    return state


def chat(step="ORIGIN", revision=0, text="Sydney"):
    return dict(
        requestId=str(uuid.uuid4()), baseRevision=revision, stepId=step, action="CHAT", text=text
    )


def confirmation(state, step):
    proposal = state.proposal
    return Turn(
        requestId=uuid.uuid4(),
        baseRevision=0,
        stepId=step,
        action="CONFIRM",
        proposalId=proposal.id,
        optionId=proposal.options[0].id,
    )


class WorkflowTests(unittest.TestCase):
    def test_model_proposal_never_confirms_and_generation_is_blocked(self):
        state = SessionState()
        apply_decision(
            state,
            Decision(stepId="ORIGIN", message="Confirm Sydney"),
            [Option(title="Sydney", answer=location())],
        )
        self.assertIsNone(state.steps[StepId.ORIGIN].answer)
        self.assertFalse(readiness(state).ready)
        confirm(state, confirmation(state, StepId.ORIGIN))
        self.assertEqual(state.currentStep, StepId.DESTINATION)
        self.assertEqual(state.steps[StepId.ORIGIN].state, "CONFIRMED")

    def test_old_option_and_locked_step_cannot_be_confirmed(self):
        state = SessionState()
        with self.assertRaises(PlanningError):
            apply_decision(state, Decision(stepId="BUDGET", message="Skip"), [])
        state.proposal = Proposal(
            stepId="ORIGIN", options=[Option(title="Sydney", answer=location())]
        )
        turn = confirmation(state, StepId.ORIGIN)
        turn.optionId = uuid.uuid4()
        with self.assertRaises(PlanningError):
            confirm(state, turn)

    def test_upstream_change_preserves_answers_but_requires_review(self):
        state = complete()
        option = Option(title="Perth", answer=location("Perth, Australia", -31.9, 115.8))
        apply_decision(state, Decision(stepId="ORIGIN", message="Change origin?"), [option])
        self.assertFalse(readiness(state).ready)
        confirm(state, confirmation(state, StepId.ORIGIN))
        self.assertEqual(state.currentStep, StepId.DESTINATION)
        self.assertEqual(state.steps[StepId.BUDGET].state, "NEEDS_REVIEW")
        self.assertEqual(state.steps[StepId.BUDGET].answer["amountMinor"], 500000)

    def test_handoff_is_valid_and_carries_requirements(self):
        state = complete()
        self.assertTrue(readiness(state).ready)
        request = generation_input(state)
        self.assertEqual(request.adults, 2)
        self.assertIn("PREFERRED: Less walking", request.constraints)
        self.assertIn("Food", request.notes["5"])
        self.assertIn("ENTIRE_PARTY_WHOLE_TRIP", request.budget)

    def test_unsupported_children_flexible_and_same_day_block_generation(self):
        for step, answer in [
            (StepId.COMPANIONS, dict(adults=2, childrenAges=[7], description="Family")),
            (StepId.DATES, dict(mode="FLEXIBLE")),
            (StepId.DATES, dict(mode="FIXED", startDate="2026-12-10", endDate="2026-12-10")),
        ]:
            state = complete()
            state.steps[step].answer = answer
            self.assertFalse(readiness(state).ready)

    def test_empty_or_malformed_turns_rejected(self):
        for value in [
            {**chat(), "text": " "},
            {**chat(), "baseRevision": True},
            {**chat(), "action": "CONFIRM"},
            {**chat(), "surprise": True},
        ]:
            with self.assertRaises(ValidationError):
                Turn.model_validate(value)


class AssistantTests(unittest.IsolatedAsyncioTestCase):
    async def test_structured_reply_and_sensitive_profile_minimization(self):
        model = AsyncMock()
        model.ainvoke.return_value = AIMessage(
            content=json.dumps(
                dict(stepId="ORIGIN", message="Confirm a location", locationQueries=["Sydney"])
            )
        )
        with patch("app.planning.assistant.build_chat_model", return_value=model):
            result = await interpret(
                SessionState(),
                {"displayName": "PRIVATE_NAME", "birthDate": "1990-01-01", "uid": "PRIVATE_UID"},
                Turn(**chat()),
            )
        self.assertEqual(result.locationQueries, ["Sydney"])
        prompt = str(model.ainvoke.call_args)
        self.assertNotIn("PRIVATE_NAME", prompt)
        self.assertNotIn("PRIVATE_UID", prompt)

    async def test_invalid_output_gets_one_repair_without_confirming(self):
        model = AsyncMock()
        model.ainvoke.side_effect = [
            AIMessage(content="not json"),
            AIMessage(
                content=json.dumps(dict(stepId="ORIGIN", message="Which city?", answer=None))
            ),
        ]
        with patch("app.planning.assistant.build_chat_model", return_value=model):
            result = await interpret(SessionState(), {}, Turn(**chat()))
        self.assertIsNone(result.answer)
        self.assertEqual(model.ainvoke.await_count, 2)

    async def test_geocoded_options_use_provider_facts_and_deduplicate(self):
        from app.tools.weather import GeoPlace

        place = GeoPlace(
            name="Sydney",
            admin1="New South Wales",
            country="Australia",
            country_code="AU",
            latitude=-33.8,
            longitude=151.2,
            timezone="Australia/Sydney",
            population=1000,
        )
        with patch("app.planning.assistant.geocode", AsyncMock(return_value=[place, place])):
            options = await options_for(
                Decision(stepId="ORIGIN", message="Confirm", locationQueries=["Sydney"])
            )
        self.assertEqual(len(options), 1)
        self.assertEqual(options[0].answer["provider"], "OPEN_METEO")


@unittest.skipUnless(os.getenv("RUN_DB_TESTS") == "1", "Set RUN_DB_TESTS=1 for PostgreSQL")
class PlanningApiTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.engine = create_engine(get_settings())
        self.connection = await self.engine.connect()
        self.transaction = await self.connection.begin()
        self.factory = async_sessionmaker(
            self.connection, expire_on_commit=False, join_transaction_mode="create_savepoint"
        )
        self.app = create_app()

        async def test_db():
            async with self.factory() as db:
                yield db

        self.app.dependency_overrides[database] = test_db

    async def asyncTearDown(self):
        await self.transaction.rollback()
        await self.connection.close()
        await self.engine.dispose()

    async def create(self):
        payload = dict(requestId=str(uuid.uuid4()))
        status, _, value = await http(self.app, "POST", "/v1/planning-sessions", payload)
        self.assertEqual(status, 201)
        return "/v1/planning-sessions/" + value["sessionId"], value, payload

    async def test_create_restore_ownership_delete_cascade(self):
        path, value, payload = await self.create()
        status, _, repeat = await http(self.app, "POST", "/v1/planning-sessions", payload)
        self.assertEqual(repeat["sessionId"], value["sessionId"])
        for method in ["GET", "DELETE"]:
            status, _, _ = await http(self.app, method, path, session="different-owner")
            self.assertEqual(status, 404)
        request = chat()
        with patch(
            "app.planning.routes.interpret",
            AsyncMock(return_value=Decision(stepId="ORIGIN", message="Which city?", answer=None)),
        ):
            status, _, _ = await http(self.app, "POST", path + "/turns", request)
        self.assertEqual(status, 200)
        status, _, _ = await http(self.app, "DELETE", path)
        self.assertEqual(status, 204)
        async with self.factory() as db:
            self.assertIsNone(
                await db.scalar(
                    select(PlanningTurn).where(
                        PlanningTurn.session_id == uuid.UUID(value["sessionId"])
                    )
                )
            )

    async def test_chat_confirm_replay_conflict_and_finalize(self):
        path, _, _ = await self.create()
        revision = 0
        for step, answer in zip(STEPS, answers(), strict=True):
            request = chat(step.value, revision, "Explicit travel facts")
            option = Option(title="Confirm answer", answer=answer)
            with (
                patch(
                    "app.planning.routes.interpret",
                    AsyncMock(return_value=Decision(stepId=step, message="Confirm this answer")),
                ),
                patch("app.planning.routes.options_for", AsyncMock(return_value=[option])),
            ):
                status, _, proposed = await http(self.app, "POST", path + "/turns", request)
                self.assertEqual(status, 200, proposed)
                status, _, replay = await http(self.app, "POST", path + "/turns", request)
                self.assertEqual(replay, proposed)
            self.assertEqual(proposed["outcome"], "PROPOSE")
            proposal = proposed["proposal"]
            confirm_request = dict(
                requestId=str(uuid.uuid4()),
                baseRevision=revision + 1,
                stepId=step.value,
                action="CONFIRM",
                proposalId=proposal["id"],
                optionId=proposal["options"][0]["id"],
            )
            status, _, confirmed = await http(self.app, "POST", path + "/turns", confirm_request)
            self.assertEqual(status, 200, confirmed)
            revision += 2
        self.assertTrue(confirmed["generationReadiness"]["ready"])
        status, _, result = await http(
            self.app, "POST", path + "/finalize", {"baseRevision": revision}
        )
        self.assertEqual(status, 200, result)
        self.assertEqual(result["generationRequest"]["adults"], 2)
        status, _, _ = await http(self.app, "POST", path + "/turns", chat(revision=0))
        self.assertEqual(status, 409)

    async def test_inflight_duplicate_and_reused_id_never_call_model(self):
        path, value, _ = await self.create()
        request = chat()
        async with self.factory() as db:
            db.add(
                PlanningTurn(
                    session_id=uuid.UUID(value["sessionId"]),
                    request_id=uuid.UUID(request["requestId"]),
                    fingerprint=fingerprint(Turn(**request)),
                    status="PROCESSING",
                )
            )
            await db.commit()
        with patch("app.planning.routes.interpret", AsyncMock()) as model:
            status, _, result = await http(self.app, "POST", path + "/turns", request)
            self.assertEqual(status, 409)
            self.assertEqual(result["detail"]["code"], "TURN_PROCESSING")
            status, _, result = await http(
                self.app, "POST", path + "/turns", {**request, "text": "Changed"}
            )
            self.assertEqual(status, 409)
            self.assertEqual(result["detail"]["code"], "REQUEST_ID_REUSED")
            model.assert_not_awaited()

    async def test_late_model_reply_cannot_overwrite_newer_revision(self):
        path, value, _ = await self.create()

        async def delayed(*args):
            async with self.factory() as db:
                await db.execute(
                    update(PlanningSession)
                    .where(PlanningSession.id == uuid.UUID(value["sessionId"]))
                    .values(revision=1)
                )
                await db.commit()
            return Decision(stepId="ORIGIN", message="Which city?")

        with patch("app.planning.routes.interpret", side_effect=delayed):
            status, _, result = await http(self.app, "POST", path + "/turns", chat())
        self.assertEqual(status, 409)
        self.assertEqual(result["detail"]["code"], "REVISION_CONFLICT")
        status, _, state = await http(self.app, "GET", path)
        self.assertEqual(state["revision"], 1)
        self.assertEqual(state["steps"]["ORIGIN"]["messages"], [])

    async def test_upstream_failure_is_replayed_and_keeps_revision(self):
        path, _, _ = await self.create()
        request = chat()
        with patch("app.planning.routes.interpret", AsyncMock(side_effect=TimeoutError)) as model:
            for _ in range(2):
                status, _, value = await http(self.app, "POST", path + "/turns", request)
                self.assertEqual(status, 504)
            self.assertEqual(model.await_count, 1)
        status, _, value = await http(self.app, "GET", path)
        self.assertEqual(value["revision"], 0)
        status, _, _ = await http(self.app, "POST", path + "/finalize", {"baseRevision": 0})
        self.assertEqual(status, 422)
