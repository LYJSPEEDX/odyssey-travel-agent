"""Look up an exchange rate and convert an amount.

uv run python -m scripts.fx_check --from AUD --to JPY --amount 1500
uv run python -m scripts.fx_check --from AUD --to USD --on 2026-10-02
"""

import argparse
import asyncio
import sys
from datetime import date
from decimal import Decimal

from app.tools.fx import convert_minor, get_rate
from app.tools.http import make_client
from app.tools.models import ToolError
from app.tools.money import minor_unit_digits, to_minor_units


async def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--from", dest="base", required=True)
    p.add_argument("--to", dest="quote", required=True)
    p.add_argument("--amount", type=Decimal, default=Decimal(100), help="in major units of --from")
    p.add_argument("--on", type=date.fromisoformat)
    a = p.parse_args()
    try:
        async with make_client() as client:
            rate = await get_rate(client, a.base, a.quote, a.on)
    except ToolError as exc:
        print(f"FAIL {type(exc).__name__}: {exc}")
        return 1
    minor = to_minor_units(a.amount, rate.base)
    out = convert_minor(minor, rate)
    d = minor_unit_digits(rate.quote)
    print(f"1 {rate.base} = {rate.rate} {rate.quote}  (ECB, as of {rate.as_of})")
    print(f"{a.amount} {rate.base} = {out / 10**d:,.{d}f} {rate.quote}  [minor units: {out}]")
    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
