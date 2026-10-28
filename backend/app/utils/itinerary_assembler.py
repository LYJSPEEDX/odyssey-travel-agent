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
from app.tools.money import minor_unit_digits


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

def _country_for_zone(zone_name: str) -> str:
    """Resolve a unique country from the system IANA timezone database."""
    from pathlib import Path
    from zoneinfo import TZPATH

    for root in TZPATH:
        path = Path(root) / "zone.tab"
        if not path.is_file():
            continue
        for line in path.read_text(encoding="utf-8").splitlines():
            if not line or line.startswith("#"):
                continue
            columns = line.split("\t")
            if len(columns) >= 3 and columns[2] == zone_name:
                return columns[0]
    raise ValueError(f"Cannot determine country for timezone: {zone_name}")


def _range(currency: str, amount: int) -> dict:
    """Represent a known amount without inventing an uncertainty range."""
    if amount < 0:
        raise ValueError("A quoted amount cannot be negative.")
    return {"currency": currency, "lowMinor": amount,
            "expectedMinor": amount, "highMinor": amount}


def _evidence() -> dict:
    """Mark information as unverified when source timestamps are unavailable."""
    return {"status": "UNVERIFIED", "sourceIds": [], "checkedAt": None}


def _moment(day, clock, zone_name: str) -> dict:
    """Convert an estimated local time into a timezone-aware moment."""
    from datetime import datetime, timezone
    from zoneinfo import ZoneInfo

    zone = ZoneInfo(zone_name)
    value = datetime.combine(day, clock, tzinfo=zone)
    if value.astimezone(timezone.utc).astimezone(zone).replace(tzinfo=None) != value.replace(tzinfo=None):
        raise ValueError(f"Schedule contains a nonexistent local time: {value}")
    if value.replace(fold=0).utcoffset() != value.replace(fold=1).utcoffset():
        raise ValueError(f"Schedule contains an ambiguous local time: {value}")
    return {"at": value.isoformat(), "timeZone": zone_name}


def _place(place_id: str, name: str, kind: str, country: str, zone: str,
           *, latitude=None, longitude=None, provider_id=None) -> dict:
    """Build a protocol place while keeping missing coordinates unknown."""
    coordinates = None
    if latitude is not None and longitude is not None:
        coordinates = {"latitude": latitude, "longitude": longitude}
    return {"id": place_id, "name": name, "kind": kind, "countryCode": country,
            "timeZone": zone, "address": None, "coordinates": coordinates,
            "providerPlaceId": provider_id, "imageIds": [], "evidence": _evidence()}


def _cost(cost_id: str, category: str, label: str, amount: int,
          currency: str, display_currency: str) -> dict:
    """Keep a whole-party quote without guessing a conversion or unit price."""
    price = _range(currency, amount)
    return {"id": cost_id, "category": category, "label": label,
            "unit": "PARTY", "quantity": 1, "unitPrice": price,
            "originalTotal": price, "displayTotal": price if currency == display_currency else None,
            "exchangeRateId": None, "taxesAndFees": "UNKNOWN", "evidence": _evidence()}


def assemble_itinerary(research: ResearchResult, schedule: ScheduleResult) -> dict:
    """Assemble a partial v1 itinerary from validated research and schedule facts."""
    from datetime import datetime, timedelta, timezone
    from uuid import uuid4

    context = build_assembly_context(research, schedule)
    request = research.request
    flight = context.flight
    inbound = flight.inbound
    if inbound is None or not flight.outbound.segments or not inbound.segments:
        raise ValueError("Both flight directions must include segments.")
    if request.adults is None or request.currency is None:
        raise ValueError("Adults and currency are required to assemble an itinerary.")
    origin_zone = flight.outbound.segments[0].departure_tz
    destination_zone = flight.outbound.segments[-1].arrival_tz
    origin_country = _country_for_zone(origin_zone)
    destination_country = _country_for_zone(destination_zone)
    start = min(request.startDate, flight.outbound.departure.date(), context.schedule.days[0].date)
    end = max(request.endDate, inbound.arrival.date(), context.schedule.days[-1].date)
    day_count = (end - start).days + 1
    if day_count > 60:
        raise ValueError("The itinerary exceeds the 60-day protocol limit.")
    places = [
        _place("place_origin", request.departure, "CITY", origin_country, origin_zone),
        _place("place_destination", request.destination, "CITY", destination_country, destination_zone),
    ]
    place_ids = {}
    for index, (label, place) in enumerate(context.places_by_label.items(), 1):
        place_id = f"place_candidate_{index}"
        place_ids[label] = place_id
        places.append(_place(place_id, place.source_title or f"Place {label}", "OTHER",
                             destination_country, destination_zone, latitude=place.latitude,
                             longitude=place.longitude, provider_id=place.place_id))
    hotel = context.hotel.offer
    places.append(_place("place_hotel", hotel.name, "HOTEL", destination_country,
                         destination_zone, latitude=hotel.latitude, longitude=hotel.longitude))
    schedules = {day.date: day for day in context.schedule.days}
    days = []
    for index in range(day_count):
        day_date = start + timedelta(days=index)
        events = []
        if day_date == flight.outbound.departure.date():
            events.append({"id": "event_outbound", "type": "TRANSPORT", "title": "Outbound flight",
                           "description": "Flight option details need confirmation.", "imageIds": [],
                           "transportGroupId": "transport_outbound"})
        schedule_day = schedules.get(day_date)
        if schedule_day:
            for position, item in enumerate(schedule_day.items, 1):
                event = {"id": f"event_{index + 1}_{position}", "type": item.type,
                         "title": item.title, "description": item.description, "imageIds": [],
                         "start": _moment(day_date, item.start_time, destination_zone),
                         "end": _moment(day_date, item.end_time, destination_zone),
                         "timingStatus": "ESTIMATED", "evidence": _evidence(),
                         "placeId": place_ids.get(item.place_label), "costItemIds": []}
                if item.type == "ACTIVITY":
                    event["details"] = {"category": "OTHER", "reservationStatus": "UNKNOWN", "openingHoursNote": None}
                elif item.type == "MEAL":
                    hour = item.start_time.hour
                    meal_type = "BREAKFAST" if hour < 11 else "LUNCH" if hour < 15 else "DINNER" if hour < 21 else "SNACK"
                    event["details"] = {"mealType": meal_type, "cuisine": "Not specified",
                                        "dietaryStatus": "UNVERIFIED", "reservationStatus": "UNKNOWN"}
                events.append(event)
        if day_date == inbound.departure.date():
            events.append({"id": "event_inbound", "type": "TRANSPORT", "title": "Return flight",
                           "description": "Flight option details need confirmation.", "imageIds": [],
                           "transportGroupId": "transport_inbound"})
        if len(events) > 40:
            raise ValueError(f"Too many events for {day_date}.")
        days.append({"id": f"day_{index + 1}", "dayNumber": index + 1, "date": day_date.isoformat(),
                     "timeZone": destination_zone if schedule_day else origin_zone,
                     "title": f"Day {index + 1}", "summary": "Travel plans and estimated activities.",
                     "walkingDistanceMeters": None, "events": events, "continuingEventIds": []})
    flight_cost = _cost("cost_flight", "INTERCITY_TRANSPORT", "Return flights for the travel party",
                        flight.price_minor, flight.currency, request.currency)
    hotel_deal = hotel.best_deal
    hotel_cost = _cost("cost_stay", "ACCOMMODATION", f"Stay at {hotel.name}",
                       hotel_deal.total_minor, hotel_deal.currency, request.currency)
    costs = [flight_cost, hotel_cost]
    subtotal = sum(item["displayTotal"]["expectedMinor"] for item in costs if item["displayTotal"] is not None)
    codes = list(dict.fromkeys([request.currency, flight.currency, hotel_deal.currency]))
    constraints = [{"id": f"constraint_{i}", "code": "CUSTOM", "strength": "SOFT", "description": value}
                   for i, value in enumerate(request.constraints, 1)]
    def pending_transport(group_id: str, title: str, from_id: str, to_id: str) -> dict:
        """Create a transport placeholder when the contract requires missing facts."""
        return {"id": group_id, "title": title, "fromPlaceId": from_id, "toPlaceId": to_id,
                "status": "PENDING", "recommendedOptionId": None, "planningOptionId": None,
                "userSelectedOptionId": None, "options": []}
    zero = _range(request.currency, 0)
    return {
        "schemaVersion": "1.0", "id": str(uuid4()), "revision": 1, "language": "en",
        "dataMode": "LIVE", "status": "PARTIAL", "generatedAt": datetime.now(timezone.utc).isoformat(),
        "requestSnapshot": {"originPlaceId": "place_origin", "destinationPlaceIds": ["place_destination"],
            "dateMode": "FIXED", "requestedStartDate": request.startDate.isoformat(),
            "requestedEndDate": request.endDate.isoformat(), "returnDateMeaning": "LEAVE_DESTINATION",
            "companions": "CUSTOM", "travelers": [{"id": f"traveler_{i}", "age": None,
                "passportCountry": None, "passportType": None, "residenceCountry": None}
                for i in range(1, request.adults + 1)],
            "budget": {"scope": "ENTIRE_PARTY_WHOLE_TRIP", "target": None, "includeIntercityTransport": True},
            "constraints": constraints, "notes": [*request.notes.values(), f"Companions: {request.companion}", f"Budget: {request.budget}"]},
        "summary": {"title": f"Trip to {request.destination}", "subtitle": f"Travel from {request.departure}",
            "startDate": start.isoformat(), "endDate": end.isoformat(), "dayCount": day_count,
            "coverImageId": None, "highlights": [], "advice": ["Activities and prices require confirmation."]},
        "places": places, "media": [],
        "transportGroups": [pending_transport("transport_outbound", "Outbound flight", "place_origin", "place_destination"),
                            pending_transport("transport_inbound", "Return flight", "place_destination", "place_origin")],
        "stayGroups": [{"id": "stay_main", "title": f"Stay at {hotel.name}",
            "checkInDate": research.check_in.isoformat(), "checkOutDate": research.check_out.isoformat(),
            "nights": (research.check_out - research.check_in).days, "status": "PENDING",
            "recommendedOptionId": None, "planningOptionId": None, "userSelectedOptionId": None, "options": []}],
        "days": days,
        "money": {"displayCurrency": request.currency, "currencies": [{"code": code, "minorUnitDigits": minor_unit_digits(code)} for code in codes],
                  "exchangeRates": [], "exchangeAdvice": []},
        "entryRequirements": [],
        "budget": {"scope": "ENTIRE_PARTY_WHOLE_TRIP", "lineItems": costs,
            "tripCostItemIds": ["cost_flight", "cost_stay"], "activeCostItemIds": ["cost_flight", "cost_stay"],
            "completeness": "PARTIAL", "subtotal": _range(request.currency, subtotal), "contingency": zero,
            "total": _range(request.currency, subtotal), "excludedCategories": ["Meals", "Activities", "Local transport", "Unverified fees"],
            "notes": ["Totals include only available same-currency quotes; no contingency has been estimated."]},
        "quality": {"sources": [], "assumptions": ["Activities and meal categories are estimates.",
            "Country codes are inferred from airport time zones and require confirmation.",
            "Return dates refer to departure from the destination.",
            "Quoted flight and hotel prices are unverified and may change."],
            "notices": [{"code": "INCOMPLETE_OPTIONS", "severity": "WARNING",
                "message": "Flight and hotel options need additional details before they can be displayed as confirmed choices.", "relatedIds": []}],
            "constraintChecks": [{"constraintId": item["id"], "status": "UNVERIFIED",
                "explanation": "This preference requires further verification.", "relatedIds": []} for item in constraints]},
    }
