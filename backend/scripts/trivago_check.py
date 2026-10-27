"""Search hotels on trivago and print the normalised offers.

    uv run python -m scripts.trivago_check --to "Kyoto, Japan" --in 2026-12-10 --out 2026-12-13 \
        --adults 2 --min-stars 4 --min-review 8.5 --feature pet_friendly
    uv run python -m scripts.trivago_check --lat 35.0116 --lon 135.7681 \
        --in 2026-12-10 --out 2026-12-13
"""

import argparse
import asyncio
import sys
from datetime import date

from app.tools.mcp import McpClient, McpError
from app.tools.models import ToolError
from app.tools.money import minor_unit_digits
from app.tools.stay_models import StayDeal
from app.tools.trivago import StayQuery, search_stays

TRIVAGO_URL = "https://mcp.trivago.com/mcp"


def _money(minor: int, currency: str) -> str:
    d = minor_unit_digits(currency)
    return f"{currency} {minor / 10**d:,.{d}f}"


def _deal(d: StayDeal) -> str:
    flags = [
        "free cancel" if d.free_cancellation else ("non-refundable" if d.non_refundable else ""),
        "breakfast" if d.breakfast_included else "",
        "" if d.url else "(no link)",
    ]
    flag_text = ", ".join(f for f in flags if f)
    return f"{d.advertiser}: {_money(d.total_minor, d.currency)} total" + (
        f" ({flag_text})" if flag_text else ""
    )


async def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--to")
    p.add_argument("--lat", type=float)
    p.add_argument("--lon", type=float)
    p.add_argument("--in", dest="check_in", type=date.fromisoformat, required=True)
    p.add_argument("--out", dest="check_out", type=date.fromisoformat, required=True)
    p.add_argument("--adults", type=int, default=2)
    p.add_argument("--child-age", type=int, action="append", default=[])
    p.add_argument("--rooms", type=int)
    p.add_argument("--country", default="AU")
    p.add_argument("--currency", default="AUD")
    p.add_argument("--min-stars", type=int)
    p.add_argument("--min-review", type=float)
    p.add_argument("--feature", action="append", default=[])
    p.add_argument("--top", type=int, default=5)
    a = p.parse_args()

    try:
        query = StayQuery(
            destination=a.to, latitude=a.lat, longitude=a.lon, check_in=a.check_in,
            check_out=a.check_out, adults=a.adults, children_ages=a.child_age, rooms=a.rooms,
            country=a.country, currency=a.currency, min_stars=a.min_stars,
            min_review=a.min_review, features=a.feature,
        )  # fmt: skip
        async with McpClient(TRIVAGO_URL) as client:
            offers = await search_stays(client, query)
    except (ValueError, McpError, ToolError) as exc:
        print(f"FAIL {type(exc).__name__}: {exc}")
        return 1
    print(f"{len(offers)} properties, {query.nights} night(s); showing {min(a.top, len(offers))}")
    for o in offers[: a.top]:
        score = (
            f"{o.review_score}/10 ({o.review_count} reviews)" if o.review_score else "no reviews"
        )
        print(f"  {o.name} | {o.stars or '?'}* | {score} | {o.city} | {o.distance_text}")
        print(f"      best: {_deal(o.best_deal)} | link: {'yes' if o.booking_url else 'no'}")
        print(f"      amenities: {', '.join(o.amenities[:6])}")
    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
