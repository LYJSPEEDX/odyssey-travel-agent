"""HTTP contracts mapped to the Android five-step TripPlanState form."""

import uuid
from datetime import date, datetime
from typing import Annotated, Literal, Self

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, model_validator

TextAnswer = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=2000)]
ShortAnswer = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=200)]


class TripRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    departure: ShortAnswer
    destination: ShortAnswer
    flexibleDates: bool = False
    startDate: date | None = None
    endDate: date | None = None
    companion: TextAnswer
    budget: TextAnswer
    constraints: list[ShortAnswer] = Field(default_factory=list, max_length=50)
    # Keys are the form step numbers, not array positions or unsent drafts.
    notes: dict[Literal["1", "2", "3", "4", "5"], TextAnswer] = Field(default_factory=dict)

    @model_validator(mode="after")
    def validate_plan(self) -> Self:
        if self.departure.casefold() == self.destination.casefold():
            raise ValueError("Departure and destination must differ")
        if self.flexibleDates:
            if self.startDate is not None or self.endDate is not None:
                raise ValueError("Flexible dates must omit startDate and endDate")
        else:
            if self.startDate is None or self.endDate is None:
                raise ValueError("Fixed dates require startDate and endDate")
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
