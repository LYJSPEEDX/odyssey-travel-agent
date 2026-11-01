"""Regression checks for city/state/country parsing; no external calls."""

import unittest

import httpx

from app.tools.weather import geocode


def row(state="Utah", country="United States", code="US"):
    return dict(
        name="Park City",
        admin1=state,
        country=country,
        country_code=code,
        latitude=40.646,
        longitude=-111.498,
        timezone="America/Denver",
    )


class GeocodingTests(unittest.IsolatedAsyncioTestCase):
    async def lookup(self, query, results, count=5):
        requested = []

        def handler(request):
            name = request.url.params["name"]
            requested.append(name)
            return httpx.Response(200, json={"results": results.get(name, [])})

        async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
            places = await geocode(client, query, count=count)
        return places, requested

    async def test_park_city_without_comma(self):
        places, requested = await self.lookup("Park City Utah", {"Park City, Utah": [row()]})
        self.assertEqual(requested, ["Park City Utah", "Park City, Utah"])
        self.assertEqual(places[0].admin1, "Utah")

    async def test_multiple_qualifiers_and_country_alias(self):
        for country in ["United States", "US", "USA", "United States of America"]:
            places, _ = await self.lookup(
                f"Park City, Utah, {country}", {"Park City, Utah": [row(), row("Kansas")]}
            )
            self.assertEqual([p.admin1 for p in places], ["Utah"])

    async def test_qualifiers_never_discarded_on_no_match(self):
        places, requested = await self.lookup(
            "Park City, Utah, Canada", {"Park City, Utah": [row()]}
        )
        self.assertEqual(places, [])
        self.assertEqual(requested, ["Park City, Utah"])

    async def test_result_limit_applied_after_hint_filtering(self):
        places, _ = await self.lookup(
            "Park City, Utah", {"Park City, Utah": [row("Kansas")] * 5 + [row()]}, count=1
        )
        self.assertEqual(len(places), 1)
        self.assertEqual(places[0].admin1, "Utah")

    async def test_multiword_region_and_missing_timezone(self):
        nsw = dict(
            row("New South Wales", "Australia", "AU"), name="Sydney", timezone="Australia/Sydney"
        )
        missing = dict(nsw)
        missing.pop("timezone")
        places, _ = await self.lookup(
            "Sydney New South Wales", {"Sydney, New South Wales": [missing, nsw]}
        )
        self.assertEqual([p.timezone for p in places], ["Australia/Sydney"])

    async def test_exact_city_preferred_over_prefix_landmarks(self):
        places, _ = await self.lookup(
            "Park City, Utah",
            {"Park City, Utah": [dict(row(), name="Park City Ski Area Heliport"), row()]},
        )
        self.assertEqual([place.name for place in places], ["Park City"])

    async def test_unpunctuated_retries_are_bounded(self):
        places, requested = await self.lookup("A B C D E F G H", {})
        self.assertEqual(places, [])
        self.assertEqual(len(requested), 5)
