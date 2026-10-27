"""Session-scoped persistence endpoints; generation workers will consume queued runs."""

import uuid
from collections.abc import AsyncIterator
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Query, Request, Response
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import Run, Trip
from app.schemas import RunResponse, TripList, TripRequest, TripResponse, TripSummary

router = APIRouter(prefix="/trips", tags=["trips"])


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


@router.post("", response_model=TripResponse, status_code=201)
async def create_trip(
    payload: TripRequest, request: Request, response: Response, db: Database
) -> TripResponse:
    """Persist a request and queued run atomically. Does not invoke an LLM yet."""
    trip = Trip(
        session_id=request.state.session_id,
        title=f"{payload.departure} → {payload.destination}",
        request=payload.model_dump(mode="json"),
    )
    db.add(trip)
    await db.flush()
    db.add(Run(trip_id=trip.id))
    await db.commit()
    await db.refresh(trip)
    response.headers["Location"] = f"/trips/{trip.id}"
    return await detail(db, trip)


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
    """Return the bare v1 document accepted by Android SavedTrip.fromContract."""
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
