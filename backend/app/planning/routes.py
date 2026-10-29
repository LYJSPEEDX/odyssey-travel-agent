"""Component HTTP boundary; shares infrastructure, never invokes generation."""

import asyncio
import uuid
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Request, Response
from sqlalchemy import delete, select, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.agents.llm import LlmConfigError
from app.planning.assistant import interpret, options_for
from app.planning.contracts import (
    STEPS,
    CreateSession,
    Finalize,
    FinalizeResponse,
    Message,
    SessionResponse,
    SessionState,
    Turn,
)
from app.planning.logic import (
    PlanningError,
    accessible,
    apply_decision,
    brief,
    confirm,
    fingerprint,
    generation_input,
    response,
)
from app.planning.models import PlanningSession, PlanningTurn

router = APIRouter(prefix="/v1/planning-sessions", tags=["planning chatbot"])


async def database(request: Request):
    async with request.app.state.session_factory() as db:
        yield db


Database = Annotated[AsyncSession, Depends(database)]


def problem(status, code, message, **extra):
    return HTTPException(status, detail={"code": code, "message": message, **extra})


async def owned(db, session_id, owner):
    row = await db.scalar(
        select(PlanningSession).where(
            PlanningSession.id == session_id, PlanningSession.owner == owner
        )
    )
    if row is None:
        raise problem(404, "SESSION_NOT_FOUND", "Planning session not found.")
    return row


@router.post("", response_model=SessionResponse, status_code=201)
async def create(payload: CreateSession, request: Request, db: Database):
    owner = request.state.session_id
    profile = payload.profile.model_dump(mode="json")
    row = await db.scalar(
        select(PlanningSession).where(
            PlanningSession.owner == owner, PlanningSession.creation_request_id == payload.requestId
        )
    )
    if row is None:
        state = SessionState()
        state.steps[state.currentStep].state = "AWAITING_INPUT"
        row = PlanningSession(
            owner=owner,
            creation_request_id=payload.requestId,
            profile=profile,
            state=state.model_dump(mode="json"),
            revision=0,
        )
        db.add(row)
        try:
            await db.commit()
        except IntegrityError:
            await db.rollback()
            row = await db.scalar(
                select(PlanningSession).where(
                    PlanningSession.owner == owner,
                    PlanningSession.creation_request_id == payload.requestId,
                )
            )
            if row is None:
                raise
    if row.profile != profile:
        raise problem(409, "REQUEST_ID_REUSED", "This requestId was used with another profile.")
    return response(
        row.id,
        row.revision,
        SessionState.model_validate(row.state),
        "CREATED",
        "Where will you depart from? Tell me a city to begin.",
        payload.requestId,
    )


@router.get("/{session_id}", response_model=SessionResponse)
async def restore(session_id: uuid.UUID, request: Request, db: Database):
    row = await owned(db, session_id, request.state.session_id)
    return response(row.id, row.revision, SessionState.model_validate(row.state), "RESTORED")


async def replay_or_reserve(db, row, turn):
    digest = fingerprint(turn)
    old = await db.scalar(
        select(PlanningTurn).where(
            PlanningTurn.session_id == row.id, PlanningTurn.request_id == turn.requestId
        )
    )
    if old:
        if old.fingerprint != digest:
            raise problem(409, "REQUEST_ID_REUSED", "This requestId was used for another action.")
        if old.status == "PROCESSING":
            raise problem(
                409, "TURN_PROCESSING", "Turn already processing. Restore before retrying."
            )
        if old.http_status != 200:
            raise HTTPException(old.http_status, detail=old.response)
        return old.response
    if row.revision != turn.baseRevision:
        raise problem(409, "REVISION_CONFLICT", "Refresh before submitting.", revision=row.revision)
    db.add(
        PlanningTurn(
            session_id=row.id, request_id=turn.requestId, fingerprint=digest, status="PROCESSING"
        )
    )
    try:
        await db.commit()
    except IntegrityError:
        await db.rollback()
        raise problem(409, "TURN_PROCESSING", "This request was submitted concurrently.") from None
    return None


async def record_error(db, session_id, request_id, error):
    await db.execute(
        update(PlanningTurn)
        .where(PlanningTurn.session_id == session_id, PlanningTurn.request_id == request_id)
        .values(status="FAILED", http_status=error.status_code, response=error.detail)
    )
    await db.commit()


@router.post("/{session_id}/turns", response_model=SessionResponse)
async def turn(session_id: uuid.UUID, payload: Turn, request: Request, db: Database):
    row = await owned(db, session_id, request.state.session_id)
    state, profile = SessionState.model_validate(row.state), dict(row.profile)
    replay = await replay_or_reserve(db, row, payload)
    if replay is not None:
        return replay
    # Reservation committed: no transaction or row lock stays open during LLM calls.
    try:
        accessible(state, payload.stepId)
        if payload.action == "CONFIRM":
            confirm(state, payload)
            outcome, message = "CONFIRMED", "Answer confirmed."
        else:
            if STEPS.index(payload.stepId) > STEPS.index(state.currentStep):
                raise PlanningError("STEP_LOCKED", "Return to the current planning step first.")
            decision = await interpret(state, profile, payload)
            if STEPS.index(decision.stepId) > STEPS.index(payload.stepId):
                raise PlanningError(
                    "INVALID_MODEL_STEP", "The assistant tried to skip a step.", 502
                )
            options = await options_for(decision)
            apply_decision(state, decision, options)
            outcome, message = ("PROPOSE" if options else "CLARIFY"), decision.message
            if decision.locationQueries and not options:
                message = "No matching locations found. Please provide the city and country."
            messages = state.steps[payload.stepId].messages
            messages.extend(
                [Message(role="USER", text=payload.text), Message(role="ASSISTANT", text=message)]
            )
            state.steps[payload.stepId].messages = messages[-16:]
        result = response(
            session_id, payload.baseRevision + 1, state, outcome, message, payload.requestId
        ).model_dump(mode="json")
        changed = await db.execute(
            update(PlanningSession)
            .where(
                PlanningSession.id == session_id,
                PlanningSession.owner == request.state.session_id,
                PlanningSession.revision == payload.baseRevision,
            )
            .values(revision=payload.baseRevision + 1, state=state.model_dump(mode="json"))
        )
        if changed.rowcount != 1:
            await db.rollback()
            raise problem(
                409, "REVISION_CONFLICT", "Session changed during processing. Refresh it."
            )
        await db.execute(
            update(PlanningTurn)
            .where(
                PlanningTurn.session_id == session_id, PlanningTurn.request_id == payload.requestId
            )
            .values(status="COMPLETED", http_status=200, response=result)
        )
        await db.commit()
        return result
    except asyncio.CancelledError:
        await record_error(
            db,
            session_id,
            payload.requestId,
            problem(409, "TURN_INTERRUPTED", "Turn interrupted; refresh before retry."),
        )
        raise
    except Exception as exc:
        if isinstance(exc, HTTPException):
            error = exc
        elif isinstance(exc, PlanningError):
            error = problem(exc.status, exc.code, str(exc))
        elif isinstance(exc, LlmConfigError):
            error = problem(503, "MODEL_CONFIGURATION", "Check the shared LLM configuration.")
        elif isinstance(exc, TimeoutError):
            error = problem(504, "ASSISTANT_TIMEOUT", "The planning assistant timed out.")
        else:
            error = problem(
                502,
                "ASSISTANT_FAILED",
                "Assistant or location service failed. "
                "Confirmed answers are unchanged; refresh and retry with a new requestId.",
            )
        await db.rollback()
        await record_error(db, session_id, payload.requestId, error)
        raise error from None


@router.post("/{session_id}/finalize", response_model=FinalizeResponse)
async def finalize(session_id: uuid.UUID, payload: Finalize, request: Request, db: Database):
    """Export this revision; does not create a trip, freeze the session or start agents."""
    row = await owned(db, session_id, request.state.session_id)
    if row.revision != payload.baseRevision:
        raise problem(
            409, "REVISION_CONFLICT", "Refresh before finalization.", revision=row.revision
        )
    state = SessionState.model_validate(row.state)
    try:
        trip = generation_input(state)
    except ValueError as exc:
        raise problem(422, "NOT_READY", str(exc)) from None
    return {
        "schemaVersion": "1.0",
        "sessionId": session_id,
        "revision": row.revision,
        "brief": brief(state),
        "generationRequest": trip.model_dump(mode="json"),
    }


@router.delete("/{session_id}", status_code=204)
async def remove(session_id: uuid.UUID, request: Request, db: Database):
    await owned(db, session_id, request.state.session_id)
    await db.execute(delete(PlanningSession).where(PlanningSession.id == session_id))
    await db.commit()
    return Response(status_code=204)
