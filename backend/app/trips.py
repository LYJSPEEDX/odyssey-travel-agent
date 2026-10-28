"""Session-scoped trip generation and persistence endpoints."""

import uuid
from datetime import datetime, timezone
from collections.abc import AsyncIterator
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Query, Request, Response
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import Run, Trip
from app.schemas import RunResponse, TripList, TripRequest, TripResponse, TripSummary
import asyncio
import logging

from app.agents.errors import AgentOutputError
from app.agents.llm import build_chat_model
from app.agents.schedule import (
    ScheduleInputError,
    build_schedule_task,
    run_schedule_agent,
)
from app.config import get_settings
from app.utils.itinerary_assembler import assemble_itinerary
from app.agents.research import (
    failure,
    redact,
    research as research_endpoint,
)

router = APIRouter(prefix="/trips", tags=["trips"])
logger = logging.getLogger("uvicorn.error.research")

async def database(request: Request) -> AsyncIterator[AsyncSession]:
    async with request.app.state.session_factory() as session:
        yield session

Database = Annotated[AsyncSession, Depends(database)]

async def owned_trip(db: AsyncSession, trip_id: uuid.UUID, session_id: str) -> Trip:
    trip = await db.scalar(select(Trip).where(Trip.id == trip_id, Trip.session_id == session_id))
    if trip is None:
        # Do not disclose whether a different session owns this ID.
        raise HTTPException(status_code=404, detail="Trip not found")
    return trip


async def detail(db: AsyncSession, trip: Trip) -> TripResponse:
    run = await db.scalar(
        select(Run)
        .where(Run.trip_id == trip.id)
        .order_by(Run.created_at.desc(), Run.id.desc())
        .limit(1)
    )
    return TripResponse(
        **TripSummary.model_validate(trip).model_dump(),
        request=trip.request,
        itinerary=trip.itinerary,
        latest_run=RunResponse.model_validate(run) if run else None,
    )

async def generate_itinerary(
        payload: TripRequest,
        response: Response,
        *, itinerary_id: str | None = None,
) -> dict:
    """Run the research, schedule and assembly pipeline without database writes."""
    research_result = await research_endpoint(
        payload,
        response,
        max_tool_turns=4,
    )

    request_id = response.headers["X-Research-Id"]
    settings = get_settings()

    secrets = tuple(
        value.strip()
        for value in (
            settings.google_maps_api_key or "",
            settings.llm_api_key or "",
        )
        if value.strip()
    )

    try:
        logger.info(
            "[research=%s][agent=schedule] started",
            request_id,
        )

        schedule_task = build_schedule_task(research_result)
        llm = build_chat_model(settings)

        async with asyncio.timeout(settings.research_timeout_s):
            schedule = await run_schedule_agent(llm, schedule_task)

    except TimeoutError as exc:
        logger.error(
            "[research=%s][agent=schedule] timed out",
            request_id,
        )
        raise failure(
            504,
            "SCHEDULE_TIMEOUT",
            "Scheduling exceeded the server time limit.",
            request_id,
        ) from exc

    except ScheduleInputError as exc:
        logger.warning(
            "[research=%s][agent=schedule] invalid input: %s",
            request_id,
            redact(str(exc), secrets),
        )
        raise failure(
            409,
            "INVALID_SCHEDULE_INPUT",
            "Research results cannot be used for scheduling.",
            request_id,
        ) from exc

    except AgentOutputError as exc:
        logger.error(
            "[research=%s][agent=schedule] invalid output: %s",
            request_id,
            redact(str(exc), secrets),
        )
        raise failure(
            502,
            "SCHEDULE_FAILED",
            "The scheduling model returned an invalid result.",
            request_id,
        ) from exc

    except Exception as exc:
        logger.error(
            "[research=%s][agent=schedule] unexpected error: %s",
            request_id,
            redact(str(exc), secrets),
        )
        raise failure(
            500,
            "SCHEDULE_ERROR",
            "Scheduling failed unexpectedly. Check server logs.",
            request_id,
        ) from exc

    logger.info(
        "[research=%s][agent=schedule] completed: %d days",
        request_id,
        len(schedule.days),
    )

    try:
        itinerary = assemble_itinerary(research_result, schedule, itinerary_id=itinerary_id)
    except Exception as exc:
        logger.error("[research=%s][assembly] failed: %s", request_id, redact(str(exc), secrets))
        raise failure(500, "ASSEMBLY_FAILED", "The itinerary could not be assembled. Check server logs.", request_id) from exc

    logger.info("[research=%s][assembly] completed", request_id)
    return itinerary


@router.post("/preview", response_model=dict)
async def preview_trip(payload: TripRequest, response: Response) -> dict:
    """Generate an itinerary without persisting it."""
    return await generate_itinerary(payload, response)


@router.post("", response_model=dict, status_code=201)
async def create_trip(
    payload: TripRequest, request: Request, response: Response, db: Database
) -> dict:
    """Generate and atomically persist a completed itinerary and successful run."""
    trip_id = uuid.uuid4()
    started_at = datetime.now(timezone.utc)
    itinerary = await generate_itinerary(payload, response, itinerary_id=str(trip_id))
    finished_at = datetime.now(timezone.utc)
    trip = Trip(
        id=trip_id,
        session_id=request.state.session_id,
        title=f"{payload.departure} → {payload.destination}",
        request=payload.model_dump(mode="json"),
        itinerary=itinerary,
        status="READY",
    )
    db.add(trip)
    await db.flush()
    db.add(Run(trip_id=trip_id, status="SUCCEEDED", started_at=started_at, finished_at=finished_at))
    await db.commit()
    response.headers["Location"] = f"/trips/{trip_id}"
    return itinerary


@router.get("", response_model=TripList)
async def list_trips(
    request: Request,
    db: Database,
    limit: Annotated[int, Query(ge=1, le=100)] = 20,
    offset: Annotated[int, Query(ge=0)] = 0,
) -> TripList:
    rows = await db.scalars(
        select(Trip)
        .where(Trip.session_id == request.state.session_id)
        .order_by(Trip.created_at.desc(), Trip.id.desc())
        .limit(limit)
        .offset(offset)
    )
    return TripList(
        items=[TripSummary.model_validate(row) for row in rows], limit=limit, offset=offset
    )


@router.get("/{trip_id}", response_model=TripResponse)
async def get_trip(trip_id: uuid.UUID, request: Request, db: Database) -> TripResponse:
    trip = await owned_trip(db, trip_id, request.state.session_id)
    return await detail(db, trip)


@router.get("/{trip_id}/runs/{run_id}", response_model=RunResponse)
async def get_run(
    trip_id: uuid.UUID, run_id: uuid.UUID, request: Request, db: Database
) -> RunResponse:
    await owned_trip(db, trip_id, request.state.session_id)
    run = await db.scalar(select(Run).where(Run.id == run_id, Run.trip_id == trip_id))
    if run is None:
        raise HTTPException(status_code=404, detail="Run not found")
    return RunResponse.model_validate(run)


@router.get("/{trip_id}/itinerary", response_model=dict)
async def get_itinerary(trip_id: uuid.UUID, request: Request, db: Database) -> dict:
    """Return the saved itinerary document for the owning session."""
    trip = await owned_trip(db, trip_id, request.state.session_id)
    if trip.status != "READY" or trip.itinerary is None:
        raise HTTPException(status_code=409, detail="Itinerary is not ready")
    return trip.itinerary


@router.delete("/{trip_id}", status_code=204)
async def delete_trip(trip_id: uuid.UUID, request: Request, db: Database) -> Response:
    trip = await owned_trip(db, trip_id, request.state.session_id)
    await db.delete(trip)
    await db.commit()
    return Response(status_code=204)
