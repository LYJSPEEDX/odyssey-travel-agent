"""trivago accommodation search (remote MCP server, no API key). Prices are metasearch quotes,
not confirmed availability."""

import re
from datetime import date
from typing import Literal

from pydantic import BaseModel, model_validator

from app.tools.mcp import McpClient
from app.tools.models import ToolError
from app.tools.money import to_minor_units
from app.tools.stay_models import StayDeal, StayOffer

Feature = Literal[
    "air_conditioning", "breakfast_included", "free_cancellation", "free_wifi", "gym",
    "kitchen", "parking", "pet_friendly", "pool", "spa",
]  # fmt: skip
_FILTER_NAMES = {
    "air_conditioning": "airConditioning",
    "breakfast_included": "breakfastIncluded",
    "free_cancellation": "freeCancellation",
    "free_wifi": "freeWiFi",
    "gym": "gym",
    "kitchen": "kitchen",
    "parking": "parking",
    "pet_friendly": "petFriendly",
    "pool": "pool",
    "spa": "spa",
}
_REVIEW_BUCKETS = (8.5, 8.0, 7.5, 7.0)  # trivago only filters at these thresholds


class StayQuery(BaseModel):
    destination: str | None = None  # free-text city / area / point of interest
    latitude: float | None = None  # alternative: search around a point
    longitude: float | None = None
    check_in: date
    check_out: date
    adults: int = 2
    children_ages: list[int] = []
    rooms: int | None = None  # must be <= adults
    country: str = "AU"  # market for pricing, ISO alpha-2
    currency: str = "AUD"
    min_stars: int | None = None  # 1-5
    min_review: float | None = None  # 0-10; rounded down to a bucket trivago supports
    features: list[Feature] = []

    @model_validator(mode="after")
    def _check(self) -> "StayQuery":
        by_point = self.latitude is not None and self.longitude is not None
        if bool(self.destination) == by_point:
            raise ValueError("give either destination or latitude+longitude")
        if self.check_out <= self.check_in:
            raise ValueError("check_out must be after check_in")
        if self.rooms is not None and self.rooms > self.adults:
            raise ValueError("rooms cannot exceed adults")
        return self

    @property
    def nights(self) -> int:
        return (self.check_out - self.check_in).days


def _arguments(q: StayQuery) -> tuple[str, dict]:
    args: dict = {
        "arrival": q.check_in.isoformat(),
        "departure": q.check_out.isoformat(),
        "adults": q.adults,
        "country": q.country,
        "currency": q.currency,
    }
    if q.children_ages:
        args["children"] = len(q.children_ages)
        args["children_ages"] = "-".join(str(a) for a in q.children_ages)
    if q.rooms:
        args["rooms"] = q.rooms
    if q.features:
        args["filters"] = {_FILTER_NAMES[f]: True for f in q.features}
    if q.min_stars:
        args["hotel_rating"] = {f"{n}star": True for n in range(q.min_stars, 6)}
    if q.min_review:
        bucket = next((b for b in _REVIEW_BUCKETS if q.min_review >= b), None)
        if bucket:
            args["review_rating"] = {f"rating{int(bucket * 10)}": True}
    if q.destination:
        return "trivago-accommodation-search", {"query": q.destination, **args}
    return "trivago-accommodation-radius-search", {
        "latitude": q.latitude,
        "longitude": q.longitude,
        **args,
    }


_NUMBER = re.compile(r"\d[\d,]*(?:\.\d+)?")


def _price_minor(text: str, currency: str) -> int:
    """'AU$1,320' / '￥74,413' -> integer minor units."""
    match = _NUMBER.search(text or "")
    if not match:
        raise ToolError(f"cannot read a price from {text!r}")
    return to_minor_units(match.group(0).replace(",", ""), currency)


def _deal(raw: dict, nights: int) -> StayDeal:
    currency = raw["currency"]
    per_night = _price_minor(raw["price_per_night"], currency)
    total = _price_minor(raw["price_per_stay"], currency)
    # Guard against a parsing mistake (not rounding): night price x nights must be close.
    if total and abs(per_night * nights - total) > 0.10 * total:
        raise ToolError(
            f"inconsistent price: {raw['price_per_night']}/night x {nights} vs "
            f"{raw['price_per_stay']} total"
        )
    listed = raw.get("price_attributes")
    attrs = {a.get("id") for a in listed or []}

    def flag(name: str) -> bool | None:
        return (name in attrs) if listed is not None else None

    return StayDeal(
        advertiser=raw.get("advertiser", ""),
        currency=currency,
        price_per_night_minor=per_night,
        total_minor=total,
        url=raw.get("deal_url"),
        free_cancellation=flag("freeCancellation"),
        breakfast_included=flag("breakfastIncluded"),
        non_refundable=flag("nonRefundable"),
    )


def _number(text: str | None, cast):
    try:
        return cast((text or "").replace(",", "")) if text else None
    except ValueError:
        return None


def _amenities(text: str | None) -> list[str]:
    return [a.strip() for a in (text or "").split(",") if a.strip()]


def parse_offers(payload: dict, nights: int) -> list[StayOffer]:
    offers = []
    for raw in payload.get("accommodations") or []:
        deals = [_deal(d, nights) for d in raw.get("deals") or []]
        if not deals:
            continue  # nothing bookable to show
        offers.append(
            StayOffer(
                id=raw["accommodation_id"],
                name=raw["accommodation_name"],
                city=raw.get("country_city", ""),
                stars=raw.get("hotel_rating") or None,
                review_score=_number(raw.get("review_rating"), float),
                review_count=_number(raw.get("review_count"), int),
                latitude=raw.get("latitude"),
                longitude=raw.get("longitude"),
                distance_text=raw.get("distance"),
                amenities=[
                    a.strip() for a in (raw.get("top_amenities") or "").split(",") if a.strip()
                ],
                image_url=raw.get("main_image"),
                deals=deals,
            )
        )
    return offers


async def search_stays(client: McpClient, query: StayQuery) -> list[StayOffer]:
    """Properties with at least one priced deal. Empty list means the source found nothing."""
    tool, arguments = _arguments(query)
    result = await client.call_tool(tool, arguments)
    payload = result.structured or {}
    if result.is_error:
        raise ToolError(f"trivago search failed: {result.text[:300]}")
    if payload.get("validation_errors"):
        problems = "; ".join(e.get("message", "?") for e in payload["validation_errors"])
        raise ToolError(f"trivago rejected the request: {problems}")
    if "accommodations" not in payload:
        raise ToolError(f"trivago returned an unexpected reply: {result.text[:200]!r}")
    try:
        return parse_offers(payload, query.nights)
    except (KeyError, ValueError, TypeError) as exc:
        raise ToolError(f"trivago response had an unexpected shape: {exc!r}") from exc
