"""Compatibility fixtures use the real assembler, with no provider calls or database."""

import json
import unittest
import uuid
from datetime import datetime, timedelta
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from fastapi import HTTPException
from jsonschema.exceptions import ValidationError

from app.agents.orchestrator import ResearchResult
from app.agents.schedule import ScheduleResult
from app.contracts import is_legacy_backend, validate_itinerary
from app.utils.itinerary_assembler import assemble_itinerary

ROOT = Path(__file__).resolve().parents[2]
STAMP = "2026-10-30T00:00:00+00:00"


def research_fixture():
    def segment(a, b, depart, arrive, az, bz):
        start, end = datetime.fromisoformat(depart), datetime.fromisoformat(arrive)
        return dict(
            from_code=a,
            to_code=b,
            from_city=a,
            to_city=b,
            from_name=a,
            to_name=b,
            departure=depart,
            arrival=arrive,
            departure_tz=az,
            arrival_tz=bz,
            duration_s=int((end - start).total_seconds()),
            carrier="XX",
            carrier_name="Fixture Air",
            flight_number="XX1",
            cabin="Economy",
        )

    def sector(segments):
        first, last = segments[0], segments[-1]
        return dict(
            from_code=first["from_code"],
            to_code=last["to_code"],
            departure=first["departure"],
            arrival=last["arrival"],
            duration_s=int(
                (
                    datetime.fromisoformat(last["arrival"])
                    - datetime.fromisoformat(first["departure"])
                ).total_seconds()
            ),
            stops=len(segments) - 1,
            segments=segments,
        )

    outbound = sector(
        [
            segment(
                "SYD",
                "SIN",
                "2026-12-10T12:00:00+11:00",
                "2026-12-10T17:00:00+08:00",
                "Australia/Sydney",
                "Asia/Singapore",
            ),
            segment(
                "SIN",
                "KIX",
                "2026-12-10T19:00:00+08:00",
                "2026-12-11T02:00:00+09:00",
                "Asia/Singapore",
                "Asia/Tokyo",
            ),
        ]
    )
    inbound = sector(
        [
            segment(
                "KIX",
                "SYD",
                "2026-12-13T21:00:00+09:00",
                "2026-12-14T08:00:00+11:00",
                "Asia/Tokyo",
                "Australia/Sydney",
            )
        ]
    )
    flight = dict(
        id="same-provider-id",
        price_minor=120000,
        currency="AUD",
        total_duration_s=90000,
        booking_url="https://example.com/flight",
        baggage={},
        outbound=outbound,
        inbound=inbound,
    )
    hotels = []
    for i in range(2):
        hotels.append(
            dict(
                rooms=2,
                offer=dict(
                    id=f"hotel-provider-{i}",
                    name=f"Fixture Hotel {i}",
                    city="Osaka, Japan",
                    stars=None,
                    review_score=None,
                    review_count=None,
                    latitude=None,
                    longitude=None,
                    distance_text=None,
                    amenities=[],
                    image_url=None,
                    deals=[
                        dict(
                            advertiser="Fixture",
                            currency="JPY" if i == 0 else "AUD",
                            price_per_night_minor=10000,
                            total_minor=20000,
                            url="https://example.com/hotel",
                            free_cancellation=None,
                            breakfast_included=None,
                            non_refundable=None,
                        )
                    ],
                ),
            )
        )
    return ResearchResult.model_validate(
        dict(
            request=dict(
                departure="Sydney",
                destination="Osaka",
                startDate="2026-12-10",
                endDate="2026-12-13",
                companion="Two adults",
                budget="JPY 20000 per day",
                adults=2,
                currency="AUD",
                constraints=["Less walking"],
                notes={"5": "Parks"},
            ),
            transport=dict(candidates=[flight], rationale="Fixture flights", searches=1, trace=[]),
            planning_flight_id=flight["id"],
            check_in="2026-12-11",
            check_out="2026-12-13",
            stay=dict(
                candidates=hotels,
                recommended_id="hotel-provider-0",
                rationale="Fixture hotels",
                rooms=2,
                searches=1,
                trace=[],
            ),
            destination=dict(
                candidates=[
                    dict(
                        label="P1",
                        place=dict(
                            place_id="same-provider-id",
                            source_title="Fixture Park",
                            google_maps_url="https://example.com/park",
                        ),
                    )
                ],
                rationale="Fixture places",
                searches=1,
                trace=[],
            ),
        )
    )


def schedule_fixture():
    return ScheduleResult.model_validate(
        dict(
            rationale="Estimated fixture schedule",
            days=[
                dict(
                    date=f"2026-12-{day}",
                    items=[
                        dict(
                            type=kind,
                            title="Fixture stop",
                            description="Unverified stop",
                            start_time="12:00:00",
                            end_time="13:00:00",
                            place_label="P1" if kind != "FREE_TIME" else None,
                        )
                    ],
                )
                for day, kind in [(11, "ACTIVITY"), (12, "MEAL"), (13, "FREE_TIME")]
            ],
        )
    )


def assembled():
    return assemble_itinerary(
        research_fixture(),
        schedule_fixture(),
        itinerary_id="backend_contract_fixture",
        generated_at=STAMP,
    )


class ItineraryContractTests(unittest.TestCase):
    def test_packaged_schema_matches_canonical(self):
        self.assertEqual(
            (ROOT / "docs/itinerary-contract/frontend-itinerary-v2.schema.json").read_bytes(),
            (ROOT / "backend/app/contracts/frontend-itinerary-v2.schema.json").read_bytes(),
        )

    def test_exported_android_fixture_is_actual_backend_output(self):
        expected = json.loads(
            (
                ROOT / "docs/itinerary-contract/examples/backend-generated-frontend-v2.json"
            ).read_text()
        )
        self.assertEqual(expected, assembled())

    def test_connecting_overnight_journey_and_unknowns(self):
        result = assembled()
        self.assertEqual(result["summary"]["dayCount"], 5)
        self.assertEqual(result["summary"]["endDate"], "2026-12-14")
        self.assertEqual(result["requestSnapshot"]["requestedEndDate"], "2026-12-13")
        self.assertIn("event_outbound", result["days"][1]["continuingEventIds"])
        self.assertIn("event_inbound", result["days"][-1]["continuingEventIds"])
        self.assertEqual(len(result["transportGroups"][0]["options"][0]["legs"]), 2)
        self.assertIsNone(result["stayGroups"][0]["options"][0]["guestCapacity"])
        self.assertIsNone(result["requestSnapshot"]["travelers"][0]["age"])
        self.assertEqual(result["status"], "PARTIAL")

    def test_foreign_hotel_is_not_free_or_converted(self):
        result = assembled()
        self.assertEqual(result["budget"]["subtotal"]["expectedMinor"], 120000)
        line = next(c for c in result["budget"]["lineItems"] if c["id"] == "cost_stay_1")
        self.assertIsNone(line["displayTotal"])
        self.assertEqual(line["originalTotal"]["currency"], "JPY")
        self.assertEqual(line["originalTotal"]["expectedMinor"], 20000)
        self.assertNotIn("cost_stay_2", result["budget"]["activeCostItemIds"])

    def test_full_journey_limit_includes_arrival_home(self):
        for extension, accepted in [(55, True), (56, False)]:
            research = research_fixture()
            delta = timedelta(days=extension)
            research.request.endDate += delta
            research.check_out += delta
            inbound = research.transport.candidates[0].inbound
            inbound.departure += delta
            inbound.arrival += delta
            for segment in inbound.segments:
                segment.departure += delta
                segment.arrival += delta
            schedule = schedule_fixture()
            prototype = schedule.days[0]
            schedule.days = [
                prototype.model_copy(deep=True)
                for _ in range((research.check_out - research.check_in).days + 1)
            ]
            for offset, day in enumerate(schedule.days):
                day.date = research.check_in + timedelta(days=offset)
            if accepted:
                self.assertEqual(assemble_itinerary(research, schedule)["summary"]["dayCount"], 60)
            else:
                with self.assertRaisesRegex(ValueError, "60-day"):
                    assemble_itinerary(research, schedule)

    def test_duplicate_provider_candidates_rejected(self):
        research = research_fixture()
        research.transport.candidates.append(research.transport.candidates[0].model_copy(deep=True))
        with self.assertRaisesRegex(ValueError, "provider IDs"):
            assemble_itinerary(research, schedule_fixture())

    def test_return_fare_alternatives_are_not_double_counted(self):
        research = research_fixture()
        alternative = research.transport.candidates[0].model_copy(deep=True)
        alternative.id = "alternative"
        alternative.price_minor = 99000
        research.transport.candidates.append(alternative)
        value = assemble_itinerary(research, schedule_fixture())
        self.assertEqual(len(value["transportGroups"][0]["options"]), 2)
        self.assertEqual(value["budget"]["subtotal"]["expectedMinor"], 120000)
        self.assertEqual(
            value["transportGroups"][0]["options"][0]["costItemIds"],
            value["transportGroups"][1]["options"][0]["costItemIds"],
        )

    def test_conflicting_flight_schedule_is_rejected(self):
        schedule = schedule_fixture()
        from datetime import time

        schedule.days[0].items[0].start_time = time(1)
        schedule.days[0].items[0].end_time = time(3)
        with self.assertRaisesRegex(ValueError, "overlaps"):
            assemble_itinerary(research_fixture(), schedule)

    def test_all_unknown_display_costs_remain_partial(self):
        research = research_fixture()
        research.transport.candidates[0].currency = "JPY"
        value = assemble_itinerary(research, schedule_fixture())
        self.assertEqual(value["budget"]["total"]["expectedMinor"], 0)
        self.assertEqual(value["budget"]["completeness"], "PARTIAL")
        active = set(value["budget"]["activeCostItemIds"])
        self.assertTrue(
            all(
                c["displayTotal"] is None for c in value["budget"]["lineItems"] if c["id"] in active
            )
        )

    def test_corrupt_documents_rejected(self):
        mutations = [
            lambda x: x["places"][0].update(id="destination"),
            lambda x: x["days"][1]["events"][0].update(placeId="missing"),
            lambda x: x["budget"]["total"].update(expectedMinor=1),
            lambda x: x["transportGroups"][0].update(planningOptionId="missing"),
            lambda x: x["budget"]["activeCostItemIds"].append("cost_stay_2"),
            lambda x: x.pop("contractId"),
        ]
        for mutate in mutations:
            value = assembled()
            mutate(value)
            with self.subTest(mutation=mutate), self.assertRaises((ValueError, ValidationError)):
                validate_itinerary(value)

    def test_legacy_formats_are_distinguished(self):
        self.assertTrue(is_legacy_backend({"schemaVersion": "2.0", "flights": {}}))
        self.assertFalse(is_legacy_backend({"schemaVersion": "1.0"}))
        self.assertFalse(is_legacy_backend(assembled()))


class LegacyReadTests(unittest.IsolatedAsyncioTestCase):
    async def test_preview_http_returns_shared_contract(self):
        from test_trips import http

        from app.main import create_app

        research = research_fixture()

        async def fake_research(payload, response, max_tool_turns):
            response.headers["X-Research-Id"] = "contract-test"
            return research

        with (
            patch("app.trips.research_endpoint", new=fake_research),
            patch("app.trips.build_chat_model", return_value=object()),
            patch("app.trips.run_schedule_agent", new=AsyncMock(return_value=schedule_fixture())),
        ):
            status, headers, document = await http(
                create_app(), "POST", "/trips/preview", research.request.model_dump(mode="json")
            )
        self.assertEqual(status, 200)
        self.assertEqual(document["contractId"], "odyssey.frontend.itinerary")
        validate_itinerary(document)

    async def test_unsupported_journey_has_actionable_http_error(self):
        from test_trips import http

        from app.main import create_app
        from app.utils.itinerary_assembler import ItineraryCompatibilityError

        research = research_fixture()

        async def fake_research(payload, response, max_tool_turns):
            response.headers["X-Research-Id"] = "contract-test"
            return research

        with (
            patch("app.trips.research_endpoint", new=fake_research),
            patch("app.trips.build_chat_model", return_value=object()),
            patch("app.trips.run_schedule_agent", new=AsyncMock(return_value=schedule_fixture())),
            patch(
                "app.trips.assemble_itinerary",
                side_effect=ItineraryCompatibilityError("Journey exceeds 60 days"),
            ),
        ):
            status, _, document = await http(
                create_app(), "POST", "/trips/preview", research.request.model_dump(mode="json")
            )
        self.assertEqual(status, 409)
        self.assertEqual(document["detail"]["code"], "UNSUPPORTED_JOURNEY_TIMELINE")

    async def test_legacy_read_returns_explicit_error_without_modifying_data(self):
        from app.trips import get_itinerary

        archived = {"schemaVersion": "2.0", "flights": {}}
        trip = SimpleNamespace(status="READY", itinerary=archived)
        with patch("app.trips.owned_trip", new=AsyncMock(return_value=trip)):
            with self.assertRaises(HTTPException) as error:
                await get_itinerary(
                    uuid.uuid4(), SimpleNamespace(state=SimpleNamespace(session_id="owner")), None
                )
        self.assertEqual(error.exception.status_code, 409)
        self.assertEqual(error.exception.detail["code"], "UNSUPPORTED_ITINERARY_CONTRACT")
        self.assertEqual(trip.itinerary, archived)

    async def test_frontend_v1_and_v2_read_unchanged(self):
        from app.trips import get_itinerary

        docs = [
            json.loads(
                (ROOT / "docs/itinerary-contract/examples/canberra-3-days.json").read_text()
            ),
            assembled(),
        ]
        for document in docs:
            trip = SimpleNamespace(status="READY", itinerary=document)
            with patch("app.trips.owned_trip", new=AsyncMock(return_value=trip)):
                result = await get_itinerary(
                    uuid.uuid4(), SimpleNamespace(state=SimpleNamespace(session_id="owner")), None
                )
            self.assertEqual(result, document)


if __name__ == "__main__":
    unittest.main()
