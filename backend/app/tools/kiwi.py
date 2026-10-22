"""Kiwi.com flight search (remote MCP server, no API key)."""

import json
from datetime import date, datetime
from typing import Literal
from zoneinfo import ZoneInfo

import airportsdata
from pydantic import BaseModel, Field

from app.tools.mcp import McpClient
from app.tools.models import (
    Baggage,
    FlightOffer,
    FlightSector,
    FlightSegment,
    ToolError,
)
from app.tools.money import to_minor_units

_AIRPORTS = airportsdata.load("IATA")


class FlightQuery(BaseModel):
    fly_from: str = Field(description="Origin: IATA code, city or airport name")
    fly_to: str = Field(description="Destination: IATA code, city or airport name")
    depart_date: date
    return_date: date | None = None
    adults: int = 1
    children: int = 0
    infants: int = 0
    cabin: Literal["M", "W", "C", "F"] | None = None  # economy/premium/business/first
    max_stops: int | None = None  # per direction
    max_flight_hours: int | None = None  # total flight duration
    max_price: int | None = None  # whole party, in `currency` major units
    currency: str = "AUD"


def _arguments(q: FlightQuery) -> dict:
    args: dict = {
        "flyFrom": q.fly_from,
        "flyTo": q.fly_to,
        "departureDate": q.depart_date.strftime("%d/%m/%Y"),
        "adults": q.adults,
        "children": q.children,
        "infants": q.infants,
        "currency": q.currency,
        "sort": "price",
    }
    optional = {
        "returnDate": q.return_date.strftime("%d/%m/%Y") if q.return_date else None,
        "cabinClass": q.cabin,
        "max_sector_stopovers": q.max_stops,
        "max_fly_duration": q.max_flight_hours,
        "price_to": q.max_price,
    }
    args.update({k: v for k, v in optional.items() if v is not None})
    return args


def _local(naive: str, code: str) -> tuple[datetime, str]:
    """Kiwi times are local wall-clock without an offset; attach the airport's zone."""
    airport = _AIRPORTS.get(code)
    if not airport:
        raise ToolError(f"unknown airport code {code!r}; cannot determine its time zone")
    tz = airport["tz"]
    return datetime.fromisoformat(naive).replace(tzinfo=ZoneInfo(tz)), tz


def _segment(raw: dict) -> FlightSegment:
    departure, departure_tz = _local(raw["departureTime"], raw["from"])
    arrival, arrival_tz = _local(raw["arrivalTime"], raw["to"])
    # Cross-check the zone mapping: elapsed time between the two local instants must match.
    elapsed = int((arrival - departure).total_seconds())
    if abs(elapsed - raw["durationSeconds"]) > 60:
        raise ToolError(
            f"time zone mismatch on {raw['from']}->{raw['to']}: "
            f"computed {elapsed}s, source says {raw['durationSeconds']}s"
        )
    return FlightSegment(
        from_code=raw["from"],
        to_code=raw["to"],
        from_city=raw.get("fromCity", ""),
        to_city=raw.get("toCity", ""),
        from_name=raw.get("fromName", ""),
        to_name=raw.get("toName", ""),
        departure=departure,
        arrival=arrival,
        departure_tz=departure_tz,
        arrival_tz=arrival_tz,
        duration_s=raw["durationSeconds"],
        carrier=raw.get("carrier", ""),
        carrier_name=raw.get("carrierName", ""),
        flight_number=raw.get("flightNumber", ""),
        cabin=raw.get("cabinClass", ""),
    )


def _sector(raw: dict) -> FlightSector:
    segments = [_segment(s) for s in raw["segments"]]
    return FlightSector(
        from_code=raw["from"],
        to_code=raw["to"],
        departure=segments[0].departure,
        arrival=segments[-1].arrival,
        duration_s=raw["durationSeconds"],
        stops=raw.get("stops", len(segments) - 1),
        segments=segments,
    )


def parse_offers(payload: dict, currency: str) -> list[FlightOffer]:
    offers = []
    for raw in payload.get("itineraries") or []:
        bags = raw.get("baggage") or {}
        offers.append(
            FlightOffer(
                id=raw["id"],
                price_minor=to_minor_units(raw["price"], payload.get("currency") or currency),
                currency=payload.get("currency") or currency,
                total_duration_s=raw["totalDurationSeconds"],
                booking_url=raw.get("bookingUrl"),
                baggage=Baggage(
                    personal_item=bags.get("personalItem", 0),
                    cabin_bag=bags.get("cabinBag", 0),
                    checked_bag=bags.get("checkedBag", 0),
                ),
                outbound=_sector(raw["outbound"]),
                inbound=_sector(raw["inbound"]) if raw.get("inbound") else None,
            )
        )
    return offers


async def search_flights(client: McpClient, query: FlightQuery) -> list[FlightOffer]:
    """Cheapest-first offers for the whole party. Empty list means the source found nothing."""
    result = await client.call_tool("search-flight", _arguments(query))
    if result.is_error:
        raise ToolError(f"Kiwi search failed: {result.text[:300]}")
    payload = result.structured
    if payload is None:
        try:
            payload = json.loads(result.text)
        except json.JSONDecodeError as exc:
            raise ToolError(f"Kiwi returned non-JSON output: {result.text[:200]}") from exc
    if payload.get("error"):
        raise ToolError(f"Kiwi error: {payload['error']}")
    try:
        return parse_offers(payload, query.currency)
    except (KeyError, ValueError, TypeError) as exc:
        raise ToolError(f"Kiwi response had an unexpected shape: {exc!r}") from exc
