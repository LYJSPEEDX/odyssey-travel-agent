"""Search flights on Kiwi and print the normalised offers.

    uv run python -m scripts.kiwi_check --from Sydney --to Osaka --depart 2026-12-10 \
        --return 2026-12-14 --adults 2 --max-stops 1
"""

import argparse
import asyncio
import sys
from datetime import date

from app.config import get_settings
from app.tools.kiwi import FlightQuery, search_flights
from app.tools.mcp import McpClient, McpError
from app.tools.models import FlightSector, ToolError
from app.tools.money import minor_unit_digits


def _fmt_sector(label: str, s: FlightSector) -> str:
    legs = " > ".join(
        f"{g.flight_number} {g.from_code} {g.departure:%b %d %H:%M} -> "
        f"{g.to_code} {g.arrival:%b %d %H:%M}"
        for g in s.segments
    )
    h, m = divmod(s.duration_s // 60, 60)
    return f"    {label}: {s.stops} stop(s), {h}h{m:02d}m | {legs}"


async def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--from", dest="fly_from", required=True)
    p.add_argument("--to", dest="fly_to", required=True)
    p.add_argument("--depart", type=date.fromisoformat, required=True)
    p.add_argument("--return", dest="ret", type=date.fromisoformat)
    p.add_argument("--adults", type=int, default=1)
    p.add_argument("--children", type=int, default=0)
    p.add_argument("--max-stops", type=int)
    p.add_argument("--max-hours", type=int)
    p.add_argument("--currency", default="AUD")
    p.add_argument("--top", type=int, default=5)
    a = p.parse_args()

    query = FlightQuery(
        fly_from=a.fly_from, fly_to=a.fly_to, depart_date=a.depart, return_date=a.ret,
        adults=a.adults, children=a.children, max_stops=a.max_stops,
        max_flight_hours=a.max_hours, currency=a.currency,
    )  # fmt: skip
    try:
        async with McpClient(get_settings().kiwi_mcp_url) as client:
            offers = await search_flights(client, query)
    except (McpError, ToolError) as exc:
        print(f"FAIL {type(exc).__name__}: {exc}")
        return 1
    print(f"{len(offers)} offers (cheapest first); showing {min(a.top, len(offers))}")
    for o in offers[: a.top]:
        digits = minor_unit_digits(o.currency)
        price = f"{o.price_minor / 10**digits:,.{digits}f} {o.currency}"
        print(f"  {price} total | {o.booking_url}")
        print(_fmt_sector("out", o.outbound))
        if o.inbound:
            print(_fmt_sector("back", o.inbound))
    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
