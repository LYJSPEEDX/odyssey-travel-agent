"""HTTP contracts mapped to the Android five-step TripPlanState form."""

import uuid
from datetime import date, datetime
from typing import Annotated, Literal, Self

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, model_validator

TextAnswer = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=2000)]
ShortAnswer = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=200)]


CurrencyCode = Annotated[
    str,
    StringConstraints(strict=True, strip_whitespace=True, to_upper=True, pattern=r"^[A-Za-z]{3}$"),
]


class TripRequest(BaseModel):
    """Keep the submitted form and explicit facts used to build agent tasks."""

    model_config = ConfigDict(extra="forbid")

    departure: ShortAnswer
    destination: ShortAnswer
    startDate: date
    endDate: date
    companion: TextAnswer
    budget: TextAnswer

    # Optional additions preserve older stored requests; generation checks required facts.
    adults: int | None = Field(default=None, strict=True, ge=1, le=20)
    currency: CurrencyCode | None = None
    constraints: list[ShortAnswer] = Field(default_factory=list, max_length=50)
    # Travel preferences keyed by form step; omit unsent drafts.
    notes: dict[Literal["1", "2", "3", "4", "5"], TextAnswer] = Field(default_factory=dict)

    @model_validator(mode="after")
    def validate_plan(self) -> Self:
        """Validate form dates and distinct departure and destination locations."""
        if self.departure.casefold() == self.destination.casefold():
            raise ValueError("Departure and destination must differ")
        days = (self.endDate - self.startDate).days + 1
        if not 1 <= days <= 60:
            raise ValueError("Trip must contain between 1 and 60 days inclusive")
        return self


class RunResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    trip_id: uuid.UUID
    status: Literal["QUEUED", "RUNNING", "SUCCEEDED", "FAILED"]
    error: dict | None
    created_at: datetime
    started_at: datetime | None
    finished_at: datetime | None


class TripSummary(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    title: str | None
    status: Literal["PENDING", "READY", "FAILED"]
    created_at: datetime
    updated_at: datetime


class TripResponse(TripSummary):
    request: TripRequest
    itinerary: dict | None
    latest_run: RunResponse | None


class TripList(BaseModel):
    items: list[TripSummary]
    limit: int
    offset: int
