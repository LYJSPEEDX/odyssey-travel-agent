"""Run: python -m unittest discover -s tests -v.

Set RUN_DB_TESTS=1 to additionally test the HTTP API against configured PostgreSQL.
Each database test runs inside an outer transaction that is rolled back.
"""

import json
import os
import unittest
import uuid

from pydantic import ValidationError
from sqlalchemy.ext.asyncio import async_sessionmaker

from app.config import get_settings
from app.db import create_engine
from app.main import create_app
from app.models import Trip
from app.schemas import TripRequest
from app.trips import database

PLAN = {
    "departure": "Sydney",
    "destination": "Kyoto",
    "flexibleDates": False,
    "startDate": "2026-11-01",
    "endDate": "2026-11-03",
    "companion": "Solo",
    "budget": "AUD 200 per person per day",
    "constraints": ["Less walking"],
    "notes": {"5": "Museums and food"},
}


async def http(app, method, path, payload=None, session="test-session-owner"):
    """Exercise the ASGI HTTP stack without a running server or extra dependencies."""
    messages = []
    body = json.dumps(payload).encode() if payload is not None else b""
    sent = False

    async def receive():
        nonlocal sent
        if not sent:
            sent = True
            return {"type": "http.request", "body": body, "more_body": False}
        return {"type": "http.disconnect"}

    async def send(message):
        messages.append(message)

    route, _, query = path.partition("?")
    headers = [(b"content-type", b"application/json")]
    if session is not None:
        headers.append((b"x-session-id", session.encode()))
    await app(
        {
            "type": "http",
            "asgi": {"version": "3.0"},
            "http_version": "1.1",
            "method": method,
            "scheme": "http",
            "path": route,
            "raw_path": route.encode(),
            "query_string": query.encode(),
            "headers": headers,
            "server": ("test", 80),
            "client": ("127.0.0.1", 1),
            "root_path": "",
        },
        receive,
        send,
    )
    start = next(m for m in messages if m["type"] == "http.response.start")
    data = b"".join(m.get("body", b"") for m in messages if m["type"] == "http.response.body")
    return start["status"], dict(start["headers"]), json.loads(data) if data else None


class ValidationTests(unittest.TestCase):
    def test_round_trip(self):
        self.assertEqual(TripRequest(**PLAN).model_dump(mode="json"), PLAN)

    def test_flexible_dates(self):
        plan = {**PLAN, "flexibleDates": True, "startDate": None, "endDate": None}
        self.assertTrue(TripRequest(**plan).flexibleDates)

    def test_invalid_plans(self):
        for changes in [
            {"destination": " sYdNeY "},
            {"departure": "  "},
            {"budget": ""},
            {"startDate": None},
            {"startDate": "2026-11-04"},
            {"endDate": "2027-01-01"},
            {"flexibleDates": True},
            {"startDate": "2026-02-30"},
            {"notes": {"6": "unknown step"}},
            {"unexpected": True},
        ]:
            with self.subTest(changes=changes), self.assertRaises(ValidationError):
                TripRequest(**{**PLAN, **changes})

    def test_inclusive_day_limit(self):
        TripRequest(**{**PLAN, "startDate": "2026-01-01", "endDate": "2026-03-01"})
        with self.assertRaises(ValidationError):
            TripRequest(**{**PLAN, "startDate": "2026-01-01", "endDate": "2026-03-02"})


class HttpValidationTests(unittest.IsolatedAsyncioTestCase):
    async def test_invalid_request_and_session_header(self):
        app = create_app()

        async def unused_db():
            yield None

        app.dependency_overrides[database] = unused_db
        status, headers, _ = await http(app, "POST", "/trips", {}, session=None)
        self.assertEqual(status, 422)
        self.assertEqual(len(headers[b"x-session-id"]), 32)
        status, _, _ = await http(app, "GET", "/trips/not-a-uuid")
        self.assertEqual(status, 422)
        status, _, _ = await http(app, "GET", "/trips?limit=101")
        self.assertEqual(status, 422)


@unittest.skipUnless(os.getenv("RUN_DB_TESTS") == "1", "Set RUN_DB_TESTS=1 for PostgreSQL")
class DatabaseApiTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.engine = create_engine(get_settings())
        self.connection = await self.engine.connect()
        self.transaction = await self.connection.begin()
        self.factory = async_sessionmaker(
            self.connection, expire_on_commit=False, join_transaction_mode="create_savepoint"
        )
        self.app = create_app()

        async def test_db():
            async with self.factory() as session:
                yield session

        self.app.dependency_overrides[database] = test_db

    async def asyncTearDown(self):
        await self.transaction.rollback()
        await self.connection.close()
        await self.engine.dispose()

    async def create(self):
        status, headers, trip = await http(self.app, "POST", "/trips", PLAN)
        self.assertEqual(status, 201)
        self.assertEqual(headers[b"location"].decode(), f"/trips/{trip['id']}")
        self.assertEqual(trip["status"], "PENDING")
        self.assertEqual(trip["latest_run"]["status"], "QUEUED")
        self.assertEqual(trip["request"], PLAN)
        self.assertIsNone(trip["itinerary"])
        return trip

    async def test_create_list_read_run_delete(self):
        trip = await self.create()
        path = f"/trips/{trip['id']}"
        status, _, listing = await http(self.app, "GET", "/trips?limit=1&offset=0")
        self.assertEqual(status, 200)
        self.assertEqual(listing["items"][0]["id"], trip["id"])
        self.assertNotIn("itinerary", listing["items"][0])
        status, _, got = await http(self.app, "GET", path)
        self.assertEqual(status, 200)
        self.assertEqual(got, trip)
        run_path = f"{path}/runs/{trip['latest_run']['id']}"
        status, _, _ = await http(self.app, "GET", run_path)
        self.assertEqual(status, 200)
        status, _, _ = await http(self.app, "GET", f"{path}/itinerary")
        self.assertEqual(status, 409)
        status, _, _ = await http(self.app, "DELETE", path)
        self.assertEqual(status, 204)
        status, _, _ = await http(self.app, "GET", path)
        self.assertEqual(status, 404)
        # Verify database cascade removed the associated run.
        from app.models import Run

        async with self.factory() as session:
            self.assertIsNone(await session.get(Run, uuid.UUID(trip["latest_run"]["id"])))

    async def test_session_isolation(self):
        trip = await self.create()
        path = f"/trips/{trip['id']}"
        for method, route in [
            ("GET", path),
            ("DELETE", path),
            ("GET", f"{path}/itinerary"),
            ("GET", f"{path}/runs/{trip['latest_run']['id']}"),
        ]:
            status, _, _ = await http(self.app, method, route, session="different-session")
            self.assertEqual(status, 404)
        status, _, listing = await http(self.app, "GET", "/trips", session="different-session")
        self.assertEqual(status, 200)
        self.assertEqual(listing["items"], [])
        status, _, _ = await http(self.app, "GET", f"/trips/{uuid.uuid4()}")
        self.assertEqual(status, 404)
        status, _, _ = await http(self.app, "GET", f"{path}/runs/{uuid.uuid4()}")
        self.assertEqual(status, 404)

    async def test_ready_itinerary_is_unwrapped(self):
        trip = await self.create()
        # This fixture checks transport shape only, not itinerary schema validation.
        document = {"schemaVersion": "1.0", "id": trip["id"]}
        async with self.factory() as session:
            stored = await session.get(Trip, uuid.UUID(trip["id"]))
            stored.status = "READY"
            stored.itinerary = document
            await session.commit()
        status, _, result = await http(self.app, "GET", f"/trips/{trip['id']}/itinerary")
        self.assertEqual(status, 200)
        self.assertEqual(result, document)
