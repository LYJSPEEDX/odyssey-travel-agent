import json
import unittest
from unittest.mock import AsyncMock, patch

from preview_fixture import fixture
from test_trips import http

from app.agents.research import ResearchResponse
from app.agents.schedule import build_schedule_task, parse_schedule
from app.main import create_app
from app.utils.itinerary_assembler import assemble_itinerary


class PreviewTests(unittest.IsolatedAsyncioTestCase):
    async def test_preview_includes_options_and_no_duplicate_costs(self):
        research, schedule = fixture()
        response = ResearchResponse.model_validate(research, from_attributes=True)

        async def research_call(payload, headers, max_tool_turns):
            headers.headers["X-Research-Id"] = "synthetic-audit"
            return response

        with (
            patch("app.trips.research_endpoint", side_effect=research_call),
            patch("app.trips.build_chat_model"),
            patch("app.trips.run_schedule_agent", AsyncMock(return_value=schedule)),
        ):
            status, _, data = await http(
                create_app(), "POST", "/trips/preview", research.request.model_dump(mode="json")
            )
        self.assertEqual(status, 200)
        itinerary = data["itinerary"]
        self.assertEqual(len(itinerary["days"]), 4)
        self.assertEqual(itinerary["days"][-1]["continuingEventIds"], ["event_inbound"])
        groups = itinerary["transportGroups"]
        self.assertTrue(all(g["planningOptionId"] and g["options"] for g in groups))
        self.assertEqual(groups[0]["options"][0]["costItemIds"], ["cost_flight"])
        self.assertEqual(groups[1]["options"][0]["costItemIds"], [])
        hotel = itinerary["stayGroups"][0]["options"][0]
        self.assertIsNone(hotel["guestCapacity"])
        self.assertEqual(hotel["roomCount"], 1)
        self.assertEqual(itinerary["budget"]["total"]["expectedMinor"], 240000)

    def test_schedule_rejects_before_arrival_and_after_departure(self):
        research, schedule = fixture()
        for day, start, end in [(0, "09:00", "10:00"), (2, "18:00", "19:00")]:
            data = schedule.model_dump(mode="json")
            data["days"][day]["items"][0].update(start_time=start, end_time=end)
            with self.assertRaisesRegex(ValueError, "after arrival"):
                parse_schedule(json.dumps(data), build_schedule_task(research))

    def test_schedule_rejects_unknown_places_and_overlap(self):
        research, schedule = fixture()
        data = schedule.model_dump(mode="json")
        item = data["days"][1]["items"][0]
        item["place_label"] = "invented"
        with self.assertRaisesRegex(ValueError, "Unknown place"):
            parse_schedule(json.dumps(data), build_schedule_task(research))
        item["place_label"] = "P1"
        data["days"][1]["items"].append(dict(item))
        with self.assertRaisesRegex(ValueError, "overlap"):
            parse_schedule(json.dumps(data), build_schedule_task(research))

    def test_assembly_refuses_invalid_schedule(self):
        research, schedule = fixture()
        schedule.days[0].items[0].place_label = "invented"
        with self.assertRaises(ValueError):
            assemble_itinerary(research, schedule)
