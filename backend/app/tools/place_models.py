"""Provider-neutral place data models for destination planning."""

from pydantic import BaseModel


class PlaceInfo(BaseModel):
    """Represent a place using facts returned by the data source."""

    place_id: str
    latitude: float | None = None
    longitude: float | None = None
    google_maps_url: str | None = None
    source_title: str | None = None


def parse_place(raw: dict) -> PlaceInfo:
    """Convert one Google place record into a PlaceInfo object."""
    location = raw.get("location") or {}
    links = raw.get("googleMapsLinks") or {}
    attribution = raw.get("attribution") or {}

    return PlaceInfo(
        place_id=raw["id"],
        source_title=attribution.get("title"),
        latitude=location.get("latitude"),
        longitude=location.get("longitude"),
        google_maps_url=links.get("placeUrl"),
    )