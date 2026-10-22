"""Profile-driven location suggestions matching docs/profile/profile.schema.json.

This initial provider uses a curated demo catalog, not an LLM or live travel data.
Profiles are used for this request only and are not persisted.
"""

from typing import Annotated, Literal, Self

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, ConfigDict, Field, StringConstraints, model_validator

Location = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=200)]
Reason = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=2000)]
Preferences = Annotated[str, StringConstraints(strip_whitespace=True, max_length=2000)]


class Contract(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)


class Profile(Contract):
    schema_version: Literal[1]
    age: Annotated[int, Field(ge=1, le=120)]
    residential_location: Location
    travel_preferences: Preferences

    @model_validator(mode="before")
    @classmethod
    def strict_version(cls, value):
        # Literal[1] alone accepts True/1.0, unlike the saved Android profile contract.
        if isinstance(value, dict) and type(value.get("schema_version")) is not int:
            raise ValueError("schema_version must be integer 1")
        return value


class LocationRecommendationRequest(Contract):
    schemaVersion: Literal["1.0"]
    profile: Profile
    origin: Location | None
    destinations: Annotated[list[Location], Field(max_length=20)]

    @model_validator(mode="after")
    def distinct_destinations(self) -> Self:
        names = [name.casefold() for name in self.destinations]
        if len(names) != len(set(names)):
            raise ValueError("Destinations must be unique")
        return self


class LocationRecommendation(Contract):
    location: Location
    reason: Reason


class LocationRecommendationResponse(Contract):
    schemaVersion: Literal["1.0"] = "1.0"
    departure: LocationRecommendation
    destinations: Annotated[list[LocationRecommendation], Field(min_length=1, max_length=20)]


# Ranking matches preference keywords only. No suitability, price or visa claims.
CATALOG = (
    ("Kyoto, Japan", ("museum", "food", "culture", "history", "relax")),
    ("Queenstown, New Zealand", ("nature", "hiking", "adventure", "outdoor")),
    ("Bali, Indonesia", ("beach", "relax", "wellness")),
    ("Melbourne, Australia", ("art", "food", "coffee", "museum")),
    ("Singapore", ("city", "food", "family", "shopping")),
)

router = APIRouter(prefix="/recommendations", tags=["recommendations"])


def suggest_locations(payload: LocationRecommendationRequest) -> LocationRecommendationResponse:
    departure = payload.origin or payload.profile.residential_location
    departure_reason = (
        "Uses your supplied departure choice; confirm before planning."
        if payload.origin
        else "Suggested from your residential location; confirm where you will actually depart."
    )
    if any(place.casefold() == departure.casefold() for place in payload.destinations):
        raise HTTPException(422, detail="Departure and destination must differ")

    if payload.destinations:
        suggestions = [
            LocationRecommendation(
                location=name,
                reason="Retains your supplied destination choice; confirm before planning.",
            )
            for name in payload.destinations
        ]
    else:
        preferences = payload.profile.travel_preferences.casefold()
        ranked = sorted(
            CATALOG,
            key=lambda entry: -sum(word in preferences for word in entry[1]),
        )
        suggestions = []
        for name, keywords in ranked:
            # Exclude a same-city suggestion even if the country suffix was omitted.
            if name.split(",")[0].strip().casefold() == departure.split(",")[0].strip().casefold():
                continue
            matches = [word for word in keywords if word in preferences]
            reason = (
                "Demo catalog suggestion matching preference keywords: " + ", ".join(matches) + "."
                if matches
                else "Demo catalog suggestion to help you start exploring."
            )
            suggestions.append(
                LocationRecommendation(
                    location=name,
                    reason=reason + " Confirm before planning; no live data was checked.",
                )
            )
            if len(suggestions) == 3:
                break

    return LocationRecommendationResponse(
        departure=LocationRecommendation(location=departure, reason=departure_reason),
        destinations=suggestions,
    )


@router.post("/locations", response_model=LocationRecommendationResponse)
async def recommend_locations(payload: LocationRecommendationRequest):
    """Suggest departure and destinations. Stateless demo rules; does not create a trip."""
    return suggest_locations(payload)
