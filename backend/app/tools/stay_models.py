"""Provider-neutral accommodation domain models."""

from pydantic import BaseModel


class StayDeal(BaseModel):
    """One advertiser's price for a stay at one property."""

    advertiser: str
    currency: str
    price_per_night_minor: int
    total_minor: int  # whole stay, all searched rooms combined (verified: 2 rooms = 2x)
    url: str | None  # only the lead deal of each property carries a booking link
    # None = the source did not say (only lead deals list attributes); never read it as False.
    free_cancellation: bool | None
    breakfast_included: bool | None
    non_refundable: bool | None


class StayOffer(BaseModel):
    id: str  # provider accommodation id
    name: str
    city: str  # provider's "City, Country" text; callers should check it matches the request
    stars: int | None
    review_score: float | None  # 0-10 guest rating
    review_count: int | None
    latitude: float | None
    longitude: float | None
    distance_text: str | None  # e.g. "Kyoto, 1.4 km to City center"
    amenities: list[str]
    image_url: str | None
    deals: list[StayDeal]  # never empty

    @property
    def booking_url(self) -> str | None:
        return next((d.url for d in self.deals if d.url), None)

    @property
    def best_deal(self) -> StayDeal:
        return min(self.deals, key=lambda d: d.total_minor)
