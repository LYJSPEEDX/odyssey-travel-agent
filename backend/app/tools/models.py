"""Provider-neutral flight domain models (what adapters return, what agents reason over)."""

from datetime import datetime

from pydantic import BaseModel


class ToolError(RuntimeError):
    """An upstream data source failed or returned something we cannot trust."""


class FlightSegment(BaseModel):
    from_code: str
    to_code: str
    from_city: str
    to_city: str
    from_name: str
    to_name: str
    departure: datetime  # timezone-aware, local to the departure airport
    arrival: datetime  # timezone-aware, local to the arrival airport
    departure_tz: str  # IANA zone of the departure airport
    arrival_tz: str
    duration_s: int
    carrier: str
    carrier_name: str
    flight_number: str
    cabin: str


class FlightSector(BaseModel):
    """One direction of travel (outbound or inbound): one or more connected segments."""

    from_code: str
    to_code: str
    departure: datetime
    arrival: datetime
    duration_s: int
    stops: int
    segments: list[FlightSegment]


class Baggage(BaseModel):
    personal_item: int = 0
    cabin_bag: int = 0
    checked_bag: int = 0


class FlightOffer(BaseModel):
    id: str  # provider offer id
    price_minor: int  # total for the whole party, integer minor units of `currency`
    currency: str
    total_duration_s: int
    booking_url: str | None
    baggage: Baggage
    outbound: FlightSector
    inbound: FlightSector | None  # None for one-way
