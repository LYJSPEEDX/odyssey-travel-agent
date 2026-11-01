"""Deterministic HTTP contract fixture for Android instrumentation ONLY.

Run from backend: uvicorn android_http_server:app --app-dir tests --port 8765
No model, provider or database calls. Never use this as the production/demo backend.
"""

import asyncio
import sys
import uuid
from pathlib import Path

from fastapi import FastAPI, HTTPException, Request, Response

sys.path.insert(0, str(Path(__file__).parent))
from test_itinerary_contract import research_fixture, schedule_fixture

from app.planning.contracts import (
    CreateSession,
    Decision,
    Finalize,
    Option,
    SessionResponse,
    SessionState,
    Turn,
)
from app.planning.logic import apply_decision, confirm, generation_input, response
from app.schemas import TripRequest
from app.session import SessionIdMiddleware
from app.utils.itinerary_assembler import assemble_itinerary

app = FastAPI(title="Android HTTP test fixture — synthetic data only")
app.add_middleware(SessionIdMiddleware)
sessions, creations, replay, trips, owners = {}, {}, {}, {}, {}
lose_generation = False
counts = {"create": 0, "turn": 0, "confirm": 0, "generation": 0}
answers = {
    "ORIGIN": dict(
        label="Sydney",
        countryCode="AU",
        latitude=-33.8,
        longitude=151.2,
        timeZone="Australia/Sydney",
        provider="OPEN_METEO",
    ),
    "DESTINATION": dict(
        label="Osaka",
        countryCode="JP",
        latitude=34.7,
        longitude=135.5,
        timeZone="Asia/Tokyo",
        provider="OPEN_METEO",
    ),
    "DATES": dict(mode="FIXED", startDate="2026-12-10", endDate="2026-12-13"),
    "COMPANIONS": dict(adults=2, childrenAges=[], description="Two adults"),
    "BUDGET": dict(
        description="AUD 5000 for the whole party and trip including flights",
        currency="AUD",
        amountMinor=500000,
        scope="ENTIRE_PARTY_WHOLE_TRIP",
        includesFlights=True,
    ),
    "CONSTRAINTS": dict(requirements=[]),
    "FOLLOW_UP_1": dict(pace="RELAXED", description="Relaxed pace"),
    "FOLLOW_UP_2": dict(preferences=["Parks and local food"]),
}


def get_session(sid, owner):
    if sid not in sessions or owners[sid] != owner:
        raise HTTPException(404, detail={"code": "SESSION_NOT_FOUND", "message": "Not found"})
    return sessions[sid]


@app.get("/healthz")
async def health():
    return {"status": "ok", "db": "ok", "fixtureOnly": True}


@app.post("/v1/planning-sessions", response_model=SessionResponse, status_code=201)
async def create(payload: CreateSession, request: Request):
    key = (request.state.session_id, str(payload.requestId))
    if key in creations:
        return creations[key]
    sid = uuid.uuid4()
    state = SessionState()
    state.steps[state.currentStep].state = "AWAITING_INPUT"
    sessions[str(sid)] = (state, 0)
    owners[str(sid)] = request.state.session_id
    counts["create"] += 1
    result = response(sid, 0, state, "CREATED", "Synthetic test session.")
    creations[key] = result
    return result


@app.get("/v1/planning-sessions/{sid}", response_model=SessionResponse)
async def restore(sid: str, request: Request):
    state, revision = get_session(sid, request.state.session_id)
    return response(sid, revision, state, "RESTORED")


@app.post("/v1/planning-sessions/{sid}/turns", response_model=SessionResponse)
async def turn(sid: str, payload: Turn, request: Request):
    state, revision = get_session(sid, request.state.session_id)
    key = (sid, str(payload.requestId))
    if key in replay:
        return replay[key]
    if payload.baseRevision != revision:
        raise HTTPException(409, detail={"code": "REVISION_CONFLICT", "message": "Restore first"})
    await asyncio.sleep(0.15)
    if payload.action == "CONFIRM":
        confirm(state, payload)
        counts["confirm"] += 1
        outcome = "CONFIRMED"
    else:
        option = Option(title="Synthetic test proposal", answer=answers[payload.stepId])
        apply_decision(
            state,
            Decision(stepId=payload.stepId, message="Review these synthetic facts."),
            [option],
        )
        counts["turn"] += 1
        outcome = "PROPOSE"
    sessions[sid] = (state, revision + 1)
    result = response(sid, revision + 1, state, outcome, "Synthetic test response.").model_dump(
        mode="json"
    )
    replay[key] = result
    if payload.text == "simulate-lost-reply":
        return Response(content="broken", media_type="application/json")
    return result


@app.post("/v1/planning-sessions/{sid}/finalize")
async def finalize(sid: str, payload: Finalize, request: Request):
    state, revision = get_session(sid, request.state.session_id)
    if payload.baseRevision != revision:
        raise HTTPException(409)
    return {
        "schemaVersion": "1.0",
        "sessionId": sid,
        "revision": revision,
        "generationRequest": generation_input(state).model_dump(mode="json"),
    }


@app.post("/trips", status_code=201)
async def generate(payload: TripRequest, request: Request):
    global lose_generation
    counts["generation"] += 1
    await asyncio.sleep(1)
    research = research_fixture()
    research.request = payload
    document = assemble_itinerary(research, schedule_fixture())
    document["dataMode"] = "MOCK"
    document["summary"]["advice"].insert(0, "Synthetic HTTP test fixture; no real travel research.")
    trips[document["id"]] = (request.state.session_id, document)
    if lose_generation:
        lose_generation = False
        return Response(content="broken", media_type="application/json", status_code=201)
    return document


@app.get("/trips")
async def listing(request: Request, limit: int = 100, offset: int = 0):
    items = [
        dict(
            id=sid, title=value["summary"]["title"], status="READY", created_at=value["generatedAt"]
        )
        for sid, (owner, value) in trips.items()
        if owner == request.state.session_id
    ]
    return dict(items=items[offset : offset + limit], limit=limit, offset=offset)


@app.get("/trips/{tid}/itinerary")
async def itinerary(tid: str, request: Request):
    if tid not in trips or trips[tid][0] != request.state.session_id:
        raise HTTPException(404)
    return trips[tid][1]


@app.get("/test/stats")
async def stats():
    return counts


@app.post("/test/lose-next-generation")
async def lose_next_generation():
    global lose_generation
    lose_generation = True
    return {"ok": True}
