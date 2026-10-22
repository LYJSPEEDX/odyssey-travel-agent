"""Resolve a place and show daily weather (forecast near term, typical beyond).

uv run python -m scripts.weather_check --place "Kyoto, Japan" --from 2026-12-10 --to 2026-12-13
uv run python -m scripts.weather_check --place Springfield --geocode-only
"""

import argparse
import asyncio
import sys
from datetime import date

from app.tools.http import make_client
from app.tools.models import ToolError
from app.tools.weather import geocode, get_weather


async def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--place", required=True)
    p.add_argument("--from", dest="start", type=date.fromisoformat)
    p.add_argument("--to", dest="end", type=date.fromisoformat)
    p.add_argument("--geocode-only", action="store_true")
    a = p.parse_args()
    try:
        async with make_client() as client:
            places = await geocode(client, a.place)
            if not places:
                print(f"FAIL no place matches {a.place!r}")
                return 1
            for pl in places[: 5 if a.geocode_only else 1]:
                print(
                    f"{pl.name}, {pl.admin1 or '-'}, {pl.country} ({pl.country_code}) "
                    f"{pl.latitude:.4f},{pl.longitude:.4f} {pl.timezone} pop={pl.population}"
                )
            if a.geocode_only:
                return 0
            if not (a.start and a.end):
                print("FAIL --from and --to are required for weather")
                return 1
            top = places[0]
            days = await get_weather(client, top.latitude, top.longitude, a.start, a.end)
    except ToolError as exc:
        print(f"FAIL {type(exc).__name__}: {exc}")
        return 1
    for d in days:
        extra = (
            f"rain chance {d.precip_probability_pct}%"
            if d.kind == "forecast"
            else f"rain on {d.rainy_day_share:.0%} of past {d.years_sampled} yrs"
        )
        print(
            f"  {d.date} [{d.kind:8}] {d.temp_min_c}..{d.temp_max_c} C, "
            f"{d.precip_mm} mm, {d.summary}, {extra}"
        )
    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
