import unittest

from test_trips import http

from app.main import create_app

PROFILE = {
    "schema_version": 1,
    "age": 25,
    "residential_location": "Sydney, Australia",
    "travel_preferences": "Museums, food, relaxed pace",
}
PAYLOAD = {"schemaVersion": "1.0", "profile": PROFILE, "origin": None, "destinations": []}


class RecommendationTests(unittest.IsolatedAsyncioTestCase):
    async def post(self, payload):
        return await http(create_app(), "POST", "/recommendations/locations", payload)

    async def test_profile_only_produces_both_locations(self):
        status, headers, result = await self.post(PAYLOAD)
        self.assertEqual(status, 200)
        self.assertIn(b"x-session-id", headers)
        self.assertEqual(set(result), {"schemaVersion", "departure", "destinations"})
        self.assertEqual(result["departure"]["location"], "Sydney, Australia")
        self.assertEqual(result["destinations"][0]["location"], "Kyoto, Japan")
        self.assertEqual(len(result["destinations"]), 3)
        for item in [result["departure"], *result["destinations"]]:
            self.assertTrue(item["reason"])
        self.assertIn("confirm", result["departure"]["reason"].lower())

    async def test_preserves_known_choices(self):
        status, _, result = await self.post(
            {**PAYLOAD, "origin": "Melbourne, Australia", "destinations": ["Paris, France"]}
        )
        self.assertEqual(status, 200)
        self.assertEqual(result["departure"]["location"], "Melbourne, Australia")
        self.assertEqual(result["destinations"][0]["location"], "Paris, France")

    async def test_preferences_change_ranking_and_exclude_departure(self):
        status, _, result = await self.post(
            {**PAYLOAD, "profile": {**PROFILE, "travel_preferences": "nature hiking adventure"}}
        )
        self.assertEqual(status, 200)
        self.assertEqual(result["destinations"][0]["location"], "Queenstown, New Zealand")
        status, _, result = await self.post({**PAYLOAD, "origin": "Kyoto"})
        self.assertEqual(status, 200)
        self.assertNotIn("Kyoto, Japan", [d["location"] for d in result["destinations"]])

    async def test_invalid_contracts(self):
        invalid = [
            {**PAYLOAD, "schemaVersion": "2.0"},
            {**PAYLOAD, "origin": "  "},
            {**PAYLOAD, "destinations": ["Paris", "paris"]},
            {**PAYLOAD, "destinations": ["Sydney, Australia"]},
            {**PAYLOAD, "destinations": [str(i) for i in range(21)]},
            {**PAYLOAD, "extra": "unknown"},
        ]
        for patch in [
            {"age": True},
            {"age": "25"},
            {"age": 0},
            {"age": 121},
            {"schema_version": True},
            {"schema_version": 1.0},
            {"residential_location": " "},
            {"travel_preferences": "x" * 2001},
        ]:
            invalid.append({**PAYLOAD, "profile": {**PROFILE, **patch}})
        invalid.append({key: value for key, value in PAYLOAD.items() if key != "origin"})
        for payload in invalid:
            with self.subTest(payload=payload):
                status, _, _ = await self.post(payload)
                self.assertEqual(status, 422)

    async def test_empty_preferences_allowed(self):
        status, _, result = await self.post(
            {**PAYLOAD, "profile": {**PROFILE, "travel_preferences": ""}}
        )
        self.assertEqual(status, 200)
        self.assertGreater(len(result["destinations"]), 0)
