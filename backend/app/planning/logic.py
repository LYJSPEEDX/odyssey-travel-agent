"""Deterministic workflow: the model cannot confirm, navigate, or finalize facts."""

import json

from pydantic import ValidationError

from app.planning.contracts import (
    ANSWER_MODELS,
    STEPS,
    TITLES,
    Decision,
    Option,
    Proposal,
    Readiness,
    SessionResponse,
    SessionState,
    StepId,
    Turn,
)
from app.schemas import TripRequest
from app.task_mapping import build_agent_tasks


class PlanningError(ValueError):
    def __init__(self, code: str, message: str, status: int = 409):
        super().__init__(message)
        self.code, self.status = code, status


def accessible(state: SessionState, step: StepId):
    if any(state.steps[s].state != "CONFIRMED" for s in STEPS[: STEPS.index(step)]):
        raise PlanningError("STEP_LOCKED", "Confirm or review earlier answers first.")


def readiness(state: SessionState) -> Readiness:
    blocking = [s.value for s in STEPS if state.steps[s].state != "CONFIRMED"]
    if not blocking:
        try:
            generation_input(state)
        except (ValueError, ValidationError) as exc:
            blocking.append(str(exc))
    return Readiness(ready=not blocking, blockingFields=blocking)


def generation_input(state: SessionState) -> TripRequest:
    if any(state.steps[s].state != "CONFIRMED" for s in STEPS):
        raise ValueError("Confirm all planning steps before generation.")
    facts = {s: ANSWER_MODELS[s].model_validate(state.steps[s].answer) for s in STEPS}
    dates, party, budget = facts[StepId.DATES], facts[StepId.COMPANIONS], facts[StepId.BUDGET]
    if dates.mode != "FIXED":
        raise ValueError("Generation requires fixed dates; return to DATES.")
    if party.childrenAges:
        raise ValueError("Child travellers are not supported by the current generator.")
    description = budget.description
    if budget.amountMinor is not None:
        # Keep exact minor-unit amounts and explicit scope without float conversion.
        description += f"; amountMinor={budget.amountMinor}"
    description += (
        f"; currency={budget.currency}; scope={budget.scope}; "
        f"includesFlights={budget.includesFlights}"
    )
    constraints = [f"{r.strength}: {r.description}" for r in facts[StepId.CONSTRAINTS].requirements]
    preferences = "; ".join(facts[StepId.FOLLOW_UP_2].preferences)
    request = TripRequest(
        departure=facts[StepId.ORIGIN].label,
        destination=facts[StepId.DESTINATION].label,
        startDate=dates.startDate,
        endDate=dates.endDate,
        companion=party.description,
        adults=party.adults,
        currency=budget.currency,
        budget=description,
        constraints=constraints,
        notes={"5": f"Pace: {facts[StepId.FOLLOW_UP_1].description}; Experiences: {preferences}"},
    )
    build_agent_tasks(request)
    return request


def confirm(state: SessionState, turn: Turn):
    proposal = state.proposal
    if proposal is None or proposal.id != turn.proposalId or proposal.stepId != turn.stepId:
        raise PlanningError("STALE_PROPOSAL", "This proposal is no longer current.")
    accessible(state, turn.stepId)
    option = next((o for o in proposal.options if o.id == turn.optionId), None)
    if option is None:
        raise PlanningError("INVALID_OPTION", "Choose an option from the current proposal.")
    answer = ANSWER_MODELS[turn.stepId].model_validate(option.answer).model_dump(mode="json")
    if turn.stepId in (StepId.ORIGIN, StepId.DESTINATION):
        other = StepId.DESTINATION if turn.stepId == StepId.ORIGIN else StepId.ORIGIN
        previous = state.steps[other].answer
        if previous and (
            previous["label"].casefold() == answer["label"].casefold()
            or (previous["latitude"], previous["longitude"])
            == (answer["latitude"], answer["longitude"])
        ):
            raise PlanningError("SAME_LOCATION", "Departure and destination must differ.", 422)
    step = state.steps[turn.stepId]
    if step.answer != answer:
        for later in STEPS[STEPS.index(turn.stepId) + 1 :]:
            if state.steps[later].answer is not None:
                state.steps[later].state = "NEEDS_REVIEW"
    step.answer, step.state = answer, "CONFIRMED"
    state.proposal = None
    pending = next((s for s in STEPS if state.steps[s].state != "CONFIRMED"), None)
    if pending:
        state.currentStep = pending
        if state.steps[pending].state == "LOCKED":
            state.steps[pending].state = "AWAITING_INPUT"
    else:
        state.currentStep = StepId.FOLLOW_UP_2


def apply_decision(state: SessionState, decision: Decision, options: list[Option]):
    accessible(state, decision.stepId)
    state.currentStep = decision.stepId
    state.proposal = None
    if options:
        state.proposal = Proposal(stepId=decision.stepId, options=options)
        # A revision of a confirmed answer is not a confirmation of its replacement.
        state.steps[decision.stepId].state = "AWAITING_CONFIRMATION"
    else:
        state.steps[decision.stepId].state = "AWAITING_CLARIFICATION"


def response(session_id, revision, state, outcome, message="", request_id=None):
    return SessionResponse(
        sessionId=session_id,
        revision=revision,
        currentStep=state.currentStep,
        question=TITLES[state.currentStep],
        outcome=outcome,
        assistantMessage=message,
        steps=state.steps,
        proposal=state.proposal,
        generationReadiness=readiness(state),
        requestId=request_id,
    )


def brief(state):
    return {s.value: state.steps[s].answer for s in STEPS}


def fingerprint(turn: Turn):
    import hashlib

    return hashlib.sha256(
        json.dumps(turn.model_dump(mode="json"), sort_keys=True).encode()
    ).hexdigest()
