"""Exchange rates from Frankfurter (European Central Bank reference rates, no API key)."""

from datetime import date
from decimal import ROUND_HALF_UP, Decimal

import httpx
from pydantic import BaseModel

from app.tools.http import get_json
from app.tools.models import ToolError
from app.tools.money import minor_unit_digits

BASE_URL = "https://api.frankfurter.dev/v1"


class ExchangeRate(BaseModel):
    """Itinerary-protocol direction: 1 `base` = `rate` `quote`. `rate` is a decimal string."""

    base: str
    quote: str
    rate: Decimal
    as_of: date  # the ECB publication date actually used (weekends roll back)


async def get_rate(
    client: httpx.AsyncClient, base: str, quote: str, on: date | None = None
) -> ExchangeRate:
    """Cross rate via the ECB's EUR-based rates.

    Asking Frankfurter for the pair directly rounds to ~4 significant digits, which is badly
    coarse for e.g. JPY->AUD (0.009). EUR-based quotes carry far more precision, so we divide.
    """
    base, quote = base.upper(), quote.upper()
    if base == quote:
        return ExchangeRate(base=base, quote=quote, rate=Decimal(1), as_of=on or date.today())
    wanted = sorted({c for c in (base, quote) if c != "EUR"})
    path = on.isoformat() if on else "latest"
    try:
        data = await get_json(
            client, f"{BASE_URL}/{path}", {"base": "EUR", "symbols": ",".join(wanted)}
        )
    except ToolError as exc:
        if "HTTP 404" in str(exc) or "HTTP 422" in str(exc):
            raise ToolError(
                f"no ECB reference rate for {base}->{quote}"
                + (f" on {on}" if on else "")
                + " (currency unsupported or date out of range)"
            ) from exc
        raise
    unsupported = [c for c in wanted if c not in (data.get("rates") or {})]
    if unsupported:
        raise ToolError(f"currency not covered by ECB reference rates: {', '.join(unsupported)}")
    try:
        per_eur = {"EUR": Decimal(1)} | {c: Decimal(str(data["rates"][c])) for c in wanted}
        rate = per_eur[quote] / per_eur[base]
        return ExchangeRate(
            base=base,
            quote=quote,
            rate=Decimal(f"{rate:.10g}"),
            as_of=date.fromisoformat(data["date"]),
        )
    except (KeyError, ValueError, TypeError, ArithmeticError) as exc:
        raise ToolError(f"unexpected Frankfurter reply: {str(data)[:200]}") from exc


def convert_minor(amount_minor: int, rate: ExchangeRate) -> int:
    """Convert integer minor units of rate.base into integer minor units of rate.quote."""
    major = Decimal(amount_minor) / (Decimal(10) ** minor_unit_digits(rate.base))
    converted = major * rate.rate * (Decimal(10) ** minor_unit_digits(rate.quote))
    return int(converted.quantize(Decimal(1), rounding=ROUND_HALF_UP))
