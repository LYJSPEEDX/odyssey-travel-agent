"""Prepare trusted research and schedule data for itinerary assembly."""

from pydantic import BaseModel

from app.agents.orchestrator import ResearchResult
from app.agents.schedule import (
    ScheduleResult,
    build_schedule_task,
    parse_schedule,
)
from app.agents.stay import StayCandidate
from app.tools.models import FlightOffer
from app.tools.place_models import PlaceInfo


class AssemblyContext(BaseModel):
    """Hold selected planning references and the validated schedule."""

    research: ResearchResult
    schedule: ScheduleResult
    flight: FlightOffer
    hotel: StayCandidate
    places_by_label: dict[str, PlaceInfo]


def build_assembly_context(
        research: ResearchResult,
        schedule: ScheduleResult,
) -> AssemblyContext:
    """Resolve planning references before building the itinerary document."""
    task = build_schedule_task(research)

    validated_schedule = parse_schedule(
        schedule.model_dump_json(),
        task,
    )

    places_by_label = {
        candidate.label: candidate.place
        for candidate in task.candidates
    }

    if len(places_by_label) != len(task.candidates):
        raise ValueError("Destination candidate labels must be unique.")

    return AssemblyContext(
        research=research,
        schedule=validated_schedule,
        flight=task.flight,
        hotel=task.stay,
        places_by_label=places_by_label,
    )


def _minutes(seconds: int) -> int:
    """Round a non-negative source duration to the nearest minute, half up."""
    if seconds < 0:
        raise ValueError("Flight duration cannot be negative.")
    return (seconds + 30) // 60


def _price(currency: str, amount: int) -> dict:
    """Return a whole-party quote in the source currency's minor units."""
    if amount < 0:
        raise ValueError("A quoted amount cannot be negative.")
    return {"currency": currency, "amountMinor": amount}


def _sector(sector) -> dict:
    """Convert a flight direction and preserve each airport's local timezone."""
    from zoneinfo import ZoneInfo

    if not sector.segments:
        raise ValueError("A flight direction must contain at least one segment.")
    segments = []
    previous = None
    for segment in sector.segments:
        for stamp, zone_name in ((segment.departure, segment.departure_tz),
                                 (segment.arrival, segment.arrival_tz)):
            if stamp.tzinfo is None or stamp.utcoffset() is None:
                raise ValueError("Flight timestamps must have time zone offsets.")
            if stamp.astimezone(ZoneInfo(zone_name)).utcoffset() != stamp.utcoffset():
                raise ValueError("Flight timestamp offset does not match airport timezone.")
        if segment.arrival <= segment.departure:
            raise ValueError("A flight segment must arrive after it departs.")
        if previous and (previous.to_code != segment.from_code or previous.arrival > segment.departure):
            raise ValueError("Flight segments must connect in chronological order.")
        segments.append({
            "departureAirport": segment.from_code, "arrivalAirport": segment.to_code,
            "departure": segment.departure.isoformat(), "arrival": segment.arrival.isoformat(),
            "departureTimeZone": segment.departure_tz, "arrivalTimeZone": segment.arrival_tz,
            "airline": segment.carrier_name, "flightNumber": segment.flight_number,
            "cabin": segment.cabin, "durationMinutes": _minutes(segment.duration_s),
        })
        previous = segment
    first, last = sector.segments[0], sector.segments[-1]
    if (sector.from_code != first.from_code or sector.to_code != last.to_code
            or sector.departure != first.departure or sector.arrival != last.arrival):
        raise ValueError("Flight direction does not match its segments.")
    return {"departureAirport": sector.from_code, "arrivalAirport": sector.to_code,
            "departure": sector.departure.isoformat(), "arrival": sector.arrival.isoformat(),
            "durationMinutes": _minutes(sector.duration_s), "stops": sector.stops,
            "segments": segments}


def _stay_deal(candidate: StayCandidate, currency: str):
    """Select one cheapest comparable deal and keep all its attributes together."""
    deals = candidate.offer.deals
    matching = [deal for deal in deals if deal.currency == currency]
    if matching:
        return min(matching, key=lambda deal: deal.total_minor)
    currencies = {deal.currency for deal in deals}
    if len(currencies) != 1:
        raise ValueError("Hotel deals in different currencies cannot be ranked without an exchange rate.")
    if not deals:
        raise ValueError("A hotel candidate must contain at least one deal.")
    return min(deals, key=lambda deal: deal.total_minor)


def assemble_itinerary(research: ResearchResult, schedule: ScheduleResult, *, itinerary_id: str | None = None) -> dict:
    """Build the v2 itinerary from the selected research and validated schedule."""
    from datetime import datetime, timezone
    from uuid import uuid4
    from zoneinfo import ZoneInfo

    context = build_assembly_context(research, schedule)
    request = research.request
    if request.adults is None or request.currency is None:
        raise ValueError("Adults and currency are required to assemble an itinerary.")
    flight_options = []
    for flight in research.transport.candidates:
        if flight.inbound is None:
            continue
        flight_options.append({
            "id": flight.id, "price": _price(flight.currency, flight.price_minor),
            "bookingUrl": flight.booking_url,
            "baggage": {"personalItem": flight.baggage.personal_item,
                        "cabinBag": flight.baggage.cabin_bag, "checkedBag": flight.baggage.checked_bag},
            "outbound": _sector(flight.outbound), "inbound": _sector(flight.inbound),
        })
    stay_options = []
    for candidate in research.stay.candidates:
        offer = candidate.offer
        deal = _stay_deal(candidate, request.currency)
        stay_options.append({
            "id": offer.id, "name": offer.name, "city": offer.city, "stars": offer.stars,
            "reviewScore": offer.review_score, "reviewCount": offer.review_count,
            "latitude": offer.latitude, "longitude": offer.longitude, "amenities": offer.amenities,
            "rooms": candidate.rooms, "price": _price(deal.currency, deal.total_minor),
            "bookingUrl": deal.url, "freeCancellation": deal.free_cancellation,
            "breakfastIncluded": deal.breakfast_included, "imageUrl": offer.image_url,
        })
    places = []
    place_ids = {}
    for index, (label, place) in enumerate(context.places_by_label.items(), 1):
        place_id = f"place_{index}"
        place_ids[label] = place_id
        places.append({"id": place_id, "name": place.source_title, "providerPlaceId": place.place_id,
                       "latitude": place.latitude, "longitude": place.longitude,
                       "googleMapsUrl": place.google_maps_url})
    if not context.flight.outbound.segments:
        raise ValueError("The planning flight has no outbound segments.")
    destination_zone = context.flight.outbound.segments[-1].arrival_tz
    ZoneInfo(destination_zone)
    days = []
    for index, day in enumerate(context.schedule.days, 1):
        items = []
        for position, item in enumerate(day.items, 1):
            items.append({"id": f"item_{index}_{position}", "type": item.type, "title": item.title,
                          "description": item.description, "startTime": item.start_time.isoformat(),
                          "endTime": item.end_time.isoformat(), "timingStatus": item.timing_status,
                          "placeId": place_ids.get(item.place_label)})
        days.append({"date": day.date.isoformat(), "dayNumber": index,
                     "timeZone": destination_zone, "items": items})
    selected_flight = next((item for item in flight_options if item['id'] == research.planning_flight_id), None)
    selected_stay = next((item for item in stay_options if item['id'] == research.stay.recommended_id), None)
    if selected_flight is None or selected_stay is None:
        raise ValueError("Planning selections must match returned candidates.")
    budget_items = [
        {"type": "FLIGHT", "label": "Return flights", **selected_flight['price']},
        {"type": "STAY", "label": "Accommodation", **selected_stay['price']},
    ]
    omitted = sorted({item['currency'] for item in budget_items if item['currency'] != request.currency})
    extra_note = (f" Amounts in {', '.join(omitted)} are excluded from the {request.currency} subtotal; "
                  "no exchange rate is available.") if omitted else ""
    itinerary = {
        "schemaVersion": "2.0", "id": itinerary_id or str(uuid4()),
        "generatedAt": datetime.now(timezone.utc).isoformat(), "request": request.model_dump(mode="json"),
        "flights": {"planningFlightId": research.planning_flight_id,
                    "rationale": research.transport.rationale, "options": flight_options},
        "stays": {"checkIn": research.check_in.isoformat(), "checkOut": research.check_out.isoformat(),
                  "planningStayId": research.stay.recommended_id, "rationale": research.stay.rationale,
                  "options": stay_options},
        "places": places, "days": days,
        "budget": {"currency": request.currency, "items": budget_items,
                   "knownTotalMinor": sum(item['amountMinor'] for item in budget_items
                                          if item['currency'] == request.currency), "isComplete": False},
        "notes": {"transport": research.transport.rationale, "stay": research.stay.rationale,
                  "destination": research.destination.rationale,
                  "schedule": context.schedule.rationale + " Meals, activities, local transport and "
                              "unverified fees are not included in the subtotal." + extra_note},
    }
    validate_itinerary(itinerary)
    return itinerary


def validate_itinerary(itinerary: dict) -> None:
    """Validate v2 JSON structure, selected IDs and known-price arithmetic."""
    import json
    from pathlib import Path
    from jsonschema import Draft202012Validator, FormatChecker

    schema_path = Path(__file__).resolve().parent.parent / "itinerary-v2.schema.json"
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    Draft202012Validator(schema, format_checker=FormatChecker()).validate(itinerary)
    flights = itinerary['flights']; stays = itinerary['stays']; places = itinerary['places']
    for items, name in ((flights['options'], 'flight'), (stays['options'], 'stay'), (places, 'place')):
        ids = [item['id'] for item in items]
        if any(not item for item in ids) or len(ids) != len(set(ids)):
            raise ValueError(f"{name} IDs must be non-empty and unique.")
    if flights['planningFlightId'] not in {item['id'] for item in flights['options']}:
        raise ValueError("Unknown planning flight ID.")
    if stays['planningStayId'] not in {item['id'] for item in stays['options']}:
        raise ValueError("Unknown planning stay ID.")
    place_ids = {place['id'] for place in places}
    item_ids = []
    for index, day in enumerate(itinerary['days'], 1):
        if day['dayNumber'] != index:
            raise ValueError("Schedule day numbers must be consecutive.")
        for item in day['items']:
            item_ids.append(item['id'])
            if item['placeId'] is not None and item['placeId'] not in place_ids:
                raise ValueError("Unknown schedule place ID.")
            if item['type'] in ('ACTIVITY', 'MEAL') and item['placeId'] is None:
                raise ValueError("Activities and meals require a place ID.")
    if any(not item for item in item_ids) or len(item_ids) != len(set(item_ids)):
        raise ValueError("Schedule item IDs must be non-empty and unique.")
    budget = itinerary['budget']
    total = sum(item['amountMinor'] for item in budget['items'] if item['currency'] == budget['currency'])
    if total != budget['knownTotalMinor']:
        raise ValueError("Budget subtotal does not match same-currency quotes.")
