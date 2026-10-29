"""Versioned public contracts and validated structured step answers."""

import uuid
from datetime import date
from enum import StrEnum
from typing import Annotated, Literal, Self

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, model_validator

from app.schemas import TripRequest

Text = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=500)]
Name = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=200)]
Currency = Annotated[
    str, StringConstraints(strip_whitespace=True, to_upper=True, pattern=r"^[A-Z]{3}$")
]


class Contract(BaseModel):
    model_config = ConfigDict(extra="forbid")


class StepId(StrEnum):
    ORIGIN = "ORIGIN"
    DESTINATION = "DESTINATION"
    DATES = "DATES"
    COMPANIONS = "COMPANIONS"
    BUDGET = "BUDGET"
    CONSTRAINTS = "CONSTRAINTS"
    FOLLOW_UP_1 = "FOLLOW_UP_1"
    FOLLOW_UP_2 = "FOLLOW_UP_2"


STEPS = list(StepId)
TITLES = dict(
    zip(
        STEPS,
        [
            "Where are you departing from?",
            "Where would you like to go?",
            "When are you travelling?",
            "Who is coming along?",
            "What is your budget?",
            "Anything we should plan around?",
            "What pace feels right?",
            "What would make this trip yours?",
        ],
        strict=True,
    )
)


class Profile(Contract):
    """Optional snapshot compatible with the current profile handoff's field names."""

    uid: uuid.UUID | None = None
    displayName: Name | None = None
    gender: Name | None = None
    birthDate: date | None = None
    legacyAgeYears: int | None = Field(default=None, strict=True, ge=1, le=120)
    nationality: Name | None = None
    residentialLocation: Name | None = None
    residenceCountryCode: str | None = Field(default=None, pattern=r"^[A-Z]{2}$")
    displayCurrency: Currency | None = None
    currencySource: Literal["MANUAL", "RESIDENCE", "DEFAULT"] | None = None
    travelTagIds: list[Name] = Field(default_factory=list, max_length=96)
    travelPreferences: str | None = Field(default=None, max_length=2000)


class CreateSession(Contract):
    schemaVersion: Literal["1.0"] = "1.0"
    requestId: uuid.UUID
    profile: Profile = Field(default_factory=Profile)


class LocationAnswer(Contract):
    label: Name
    countryCode: str = Field(pattern=r"^[A-Z]{2}$")
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    timeZone: Name
    provider: Literal["OPEN_METEO"] = "OPEN_METEO"


class DatesAnswer(Contract):
    mode: Literal["FIXED", "FLEXIBLE"]
    startDate: date | None = None
    endDate: date | None = None

    @model_validator(mode="after")
    def dates(self) -> Self:
        if self.mode == "FIXED":
            if self.startDate is None or self.endDate is None:
                raise ValueError("Fixed dates require startDate and endDate.")
            if not 1 <= (self.endDate - self.startDate).days + 1 <= 60:
                raise ValueError("Use an ordered date range of 1–60 days.")
        elif self.startDate is not None or self.endDate is not None:
            raise ValueError("Flexible dates must not include fixed dates.")
        return self


class PartyAnswer(Contract):
    adults: int = Field(strict=True, ge=1, le=20)
    childrenAges: list[Annotated[int, Field(strict=True, ge=0, le=17)]] = Field(max_length=20)
    description: Text


class BudgetAnswer(Contract):
    description: Text
    currency: Currency
    amountMinor: int | None = Field(default=None, strict=True, ge=0, le=10**14)
    scope: Literal[
        "ENTIRE_PARTY_WHOLE_TRIP",
        "PER_PERSON_WHOLE_TRIP",
        "ENTIRE_PARTY_PER_DAY",
        "PER_PERSON_PER_DAY",
    ]
    includesFlights: bool = Field(strict=True)


class Requirement(Contract):
    description: Annotated[
        str, StringConstraints(strip_whitespace=True, min_length=1, max_length=180)
    ]
    strength: Literal["ESSENTIAL", "PREFERRED"]


class RequirementsAnswer(Contract):
    requirements: list[Requirement] = Field(max_length=30)


class PaceAnswer(Contract):
    pace: Literal["RELAXED", "BALANCED", "PACKED", "CUSTOM"]
    description: Text


class ExperienceAnswer(Contract):
    preferences: list[
        Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=120)]
    ] = Field(min_length=1, max_length=10)


ANSWER_MODELS = dict(
    zip(
        STEPS,
        [
            LocationAnswer,
            LocationAnswer,
            DatesAnswer,
            PartyAnswer,
            BudgetAnswer,
            RequirementsAnswer,
            PaceAnswer,
            ExperienceAnswer,
        ],
        strict=True,
    )
)


class Turn(Contract):
    requestId: uuid.UUID
    baseRevision: int = Field(strict=True, ge=0)
    stepId: StepId
    action: Literal["CHAT", "REFINE", "CONFIRM", "SUBMIT"]
    text: Text | None = None
    proposalId: uuid.UUID | None = None
    optionId: uuid.UUID | None = None

    @model_validator(mode="after")
    def shape(self) -> Self:
        if self.action == "CONFIRM":
            if self.proposalId is None or self.optionId is None or self.text is not None:
                raise ValueError("Confirmation requires proposalId and optionId, without text.")
        elif self.text is None or self.proposalId is not None or self.optionId is not None:
            raise ValueError("Conversation requires text without confirmation IDs.")
        return self


class Finalize(Contract):
    baseRevision: int = Field(strict=True, ge=0)


class Message(Contract):
    role: Literal["USER", "ASSISTANT"]
    text: str = Field(max_length=2000)


class Option(Contract):
    id: uuid.UUID = Field(default_factory=uuid.uuid4)
    title: Text
    answer: dict


class Proposal(Contract):
    id: uuid.UUID = Field(default_factory=uuid.uuid4)
    stepId: StepId
    options: list[Option] = Field(min_length=1, max_length=5)


class Step(Contract):
    state: Literal[
        "LOCKED",
        "AWAITING_INPUT",
        "AWAITING_CLARIFICATION",
        "AWAITING_CONFIRMATION",
        "CONFIRMED",
        "NEEDS_REVIEW",
    ] = "LOCKED"
    answer: dict | None = None
    messages: list[Message] = Field(default_factory=list, max_length=16)


class SessionState(Contract):
    currentStep: StepId = StepId.ORIGIN
    steps: dict[StepId, Step] = Field(default_factory=lambda: {s: Step() for s in STEPS})
    proposal: Proposal | None = None


class Readiness(Contract):
    ready: bool
    blockingFields: list[str]


class SessionResponse(Contract):
    schemaVersion: Literal["1.0"] = "1.0"
    sessionId: uuid.UUID
    revision: int
    currentStep: StepId
    question: str
    outcome: Literal["CREATED", "RESTORED", "CLARIFY", "PROPOSE", "CONFIRMED"]
    assistantMessage: str
    steps: dict[StepId, Step]
    proposal: Proposal | None
    generationReadiness: Readiness
    requestId: uuid.UUID | None = None


class Decision(Contract):
    """Untrusted model proposal; confirmation and navigation are never model outputs."""

    stepId: StepId
    message: Text
    answer: dict | None = None
    locationQueries: list[Name] = Field(default_factory=list, max_length=3)


class FinalizeResponse(Contract):
    schemaVersion: Literal["1.0"] = "1.0"
    sessionId: uuid.UUID
    revision: int
    brief: dict[StepId, dict]
    generationRequest: TripRequest
