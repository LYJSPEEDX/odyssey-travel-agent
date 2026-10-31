"""Prepare trusted research and schedule data for itinerary assembly."""

from datetime import UTC

from pydantic import BaseModel

from app.agents.orchestrator import ResearchResult
from app.agents.schedule import (
    ScheduleResult,
    build_schedule_task,
    parse_schedule,
)
from app.agents.stay import StayCandidate
from app.contracts import validate_itinerary
from app.tools.models import FlightOffer
from app.tools.place_models import PlaceInfo


class ItineraryCompatibilityError(ValueError):
    """A researched journey cannot fit the supported itinerary timeline."""


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

    places_by_label = {candidate.label: candidate.place for candidate in task.candidates}

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
        for stamp, zone_name in (
            (segment.departure, segment.departure_tz),
            (segment.arrival, segment.arrival_tz),
        ):
            if stamp.tzinfo is None or stamp.utcoffset() is None:
                raise ValueError("Flight timestamps must have time zone offsets.")
            if stamp.astimezone(ZoneInfo(zone_name)).utcoffset() != stamp.utcoffset():
                raise ValueError("Flight timestamp offset does not match airport timezone.")
        if segment.arrival <= segment.departure:
            raise ValueError("A flight segment must arrive after it departs.")
        if previous and (
            previous.to_code != segment.from_code or previous.arrival > segment.departure
        ):
            raise ValueError("Flight segments must connect in chronological order.")
        segments.append(
            {
                "departureAirport": segment.from_code,
                "arrivalAirport": segment.to_code,
                "departure": segment.departure.isoformat(),
                "arrival": segment.arrival.isoformat(),
                "departureTimeZone": segment.departure_tz,
                "arrivalTimeZone": segment.arrival_tz,
                "airline": segment.carrier_name,
                "flightNumber": segment.flight_number,
                "cabin": segment.cabin,
                "durationMinutes": _minutes(segment.duration_s),
            }
        )
        previous = segment
    first, last = sector.segments[0], sector.segments[-1]
    if (
        sector.from_code != first.from_code
        or sector.to_code != last.to_code
        or sector.departure != first.departure
        or sector.arrival != last.arrival
    ):
        raise ValueError("Flight direction does not match its segments.")
    return {
        "departureAirport": sector.from_code,
        "arrivalAirport": sector.to_code,
        "departure": sector.departure.isoformat(),
        "arrival": sector.arrival.isoformat(),
        "durationMinutes": _minutes(sector.duration_s),
        "stops": sector.stops,
        "segments": segments,
    }


def _stay_deal(candidate: StayCandidate, currency: str):
    """Select one cheapest comparable deal and keep all its attributes together."""
    deals = candidate.offer.deals
    matching = [deal for deal in deals if deal.currency == currency]
    if matching:
        return min(matching, key=lambda deal: deal.total_minor)
    currencies = {deal.currency for deal in deals}
    if len(currencies) != 1:
        raise ValueError(
            "Hotel deals in different currencies cannot be ranked without an exchange rate."
        )
    if not deals:
        raise ValueError("A hotel candidate must contain at least one deal.")
    return min(deals, key=lambda deal: deal.total_minor)


def assemble_itinerary(
    research: ResearchResult,
    schedule: ScheduleResult,
    *,
    itinerary_id: str | None = None,
    generated_at: str | None = None,
) -> dict:
    """Map trusted research to the canonical Android contract, without guessed facts."""
    from datetime import datetime, timedelta
    from uuid import uuid4
    from zoneinfo import ZoneInfo

    from app.contracts import CONTRACT_ID
    from app.tools.money import minor_unit_digits

    for candidates, identifiers in (
        (research.transport.candidates, [f.id for f in research.transport.candidates]),
        (research.stay.candidates, [h.offer.id for h in research.stay.candidates]),
    ):
        if (
            not candidates
            or any(not value for value in identifiers)
            or len(set(identifiers)) != len(identifiers)
        ):
            raise ValueError(
                "Candidate provider IDs must be non-empty and unique within each source."
            )
    context = build_assembly_context(research, schedule)
    request = research.request
    if request.adults is None or request.currency is None:
        raise ValueError("Adults and currency are required to assemble an itinerary.")
    stamp = generated_at or datetime.now(UTC).isoformat()
    unverified = {"status": "UNVERIFIED", "sourceIds": [], "checkedAt": None}
    sources, places, costs = [], [], []

    def evidence(title, url):
        if not url:
            return dict(unverified)
        source_id = f"source_{len(sources) + 1}"
        sources.append({"id": source_id, "title": title, "url": url, "retrievedAt": stamp})
        return {"status": "SOURCED", "sourceIds": [source_id], "checkedAt": stamp}

    def place(
        pid, name, kind, zone=None, latitude=None, longitude=None, provider_id=None, proof=None
    ):
        places.append(
            {
                "id": pid,
                "name": name,
                "kind": kind,
                "countryCode": None,
                "timeZone": zone,
                "address": None,
                "coordinates": (
                    {"latitude": latitude, "longitude": longitude}
                    if latitude is not None and longitude is not None
                    else None
                ),
                "providerPlaceId": provider_id,
                "imageIds": [],
                "evidence": proof or dict(unverified),
            }
        )
        return pid

    def moment(value, zone):
        return {"at": value.isoformat(), "timeZone": zone}

    def price_range(currency, amount):
        if amount < 0:
            raise ValueError("Quote cannot be negative.")
        return {
            "currency": currency,
            "lowMinor": amount,
            "expectedMinor": amount,
            "highMinor": amount,
        }

    def cost(cid, category, label, currency, amount, proof):
        quote = price_range(currency, amount)
        costs.append(
            {
                "id": cid,
                "category": category,
                "label": label,
                "unit": "PARTY",
                "quantity": 1,
                "unitPrice": quote,
                "originalTotal": quote,
                "displayTotal": quote if currency == request.currency else None,
                "exchangeRateId": None,
                "taxesAndFees": "UNKNOWN",
                "evidence": proof,
            }
        )
        return cid

    selected = context.flight
    origin_zone = selected.outbound.segments[0].departure_tz
    destination_zone = selected.outbound.segments[-1].arrival_tz
    place("origin", request.departure, "CITY", origin_zone)
    place("destination", request.destination, "CITY", destination_zone)
    airport_ids = {}

    def airport(code, name, zone):
        if code not in airport_ids:
            airport_ids[code] = place(f"airport_{len(airport_ids) + 1}", name, "AIRPORT", zone)
        return airport_ids[code]

    # Both directional options refer to one indivisible return fare. Active costs are a set.
    candidates = [
        f
        for f in research.transport.candidates
        if f.inbound is not None
        and (f.outbound.from_code, f.outbound.to_code, f.inbound.from_code, f.inbound.to_code)
        == (
            selected.outbound.from_code,
            selected.outbound.to_code,
            selected.inbound.from_code,
            selected.inbound.to_code,
        )
    ]
    if len(candidates) > 5 or selected not in candidates:
        raise ValueError(
            "Expected at most five comparable flight candidates including the selection."
        )
    flight_costs = {}
    for i, flight in enumerate(candidates, 1):
        flight_costs[flight.id] = cost(
            f"cost_flight_{i}",
            "INTERCITY_TRANSPORT",
            f"Return airfare option {i} · entire party",
            flight.currency,
            flight.price_minor,
            evidence("Flight quote", flight.booking_url),
        )
    groups = []
    for direction in ("outbound", "inbound"):
        options, planned = [], None
        for i, flight in enumerate(candidates, 1):
            sector = getattr(flight, direction)
            _sector(sector)  # Validate connection order and airport offsets before mapping.
            legs = []
            for j, segment in enumerate(sector.segments, 1):
                legs.append(
                    {
                        "id": f"leg_{direction}_{i}_{j}",
                        "mode": "FLIGHT",
                        "fromPlaceId": airport(
                            segment.from_code, segment.from_name, segment.departure_tz
                        ),
                        "toPlaceId": airport(segment.to_code, segment.to_name, segment.arrival_tz),
                        "departure": moment(segment.departure, segment.departure_tz),
                        "arrival": moment(segment.arrival, segment.arrival_tz),
                        "durationMinutes": _minutes(segment.duration_s),
                        "distanceMeters": None,
                        "distanceBasis": "UNKNOWN",
                        "timingStatus": "SCHEDULED",
                        "evidence": dict(unverified),
                        "details": {
                            "marketingAirline": segment.carrier_name or None,
                            "operatingAirline": None,
                            "flightNumber": segment.flight_number or None,
                            "departureAirportCode": segment.from_code,
                            "arrivalAirportCode": segment.to_code,
                            "departureTerminal": None,
                            "arrivalTerminal": None,
                            "checkedBaggageKgPerPerson": None,
                            "isSelfTransfer": None,
                        },
                    }
                )
            oid = f"option_{direction}_{i}"
            if flight.id == selected.id:
                planned = oid
            options.append(
                {
                    "id": oid,
                    "title": f"{direction.title()} · return fare option {i}",
                    "reason": research.transport.rationale
                    + (
                        " Paired outbound and return options share one return fare; "
                        "do not mix fares."
                    ),
                    "legs": legs,
                    "costItemIds": [flight_costs[flight.id]],
                }
            )
        groups.append(
            {
                "id": direction,
                "title": f"{direction.title()} flights",
                "fromPlaceId": options[0]["legs"][0]["fromPlaceId"],
                "toPlaceId": options[0]["legs"][-1]["toPlaceId"],
                "status": "AVAILABLE",
                "recommendedOptionId": planned,
                "planningOptionId": planned,
                "userSelectedOptionId": None,
                "options": options,
            }
        )

    stay_options, stay_selection = [], None
    for i, candidate in enumerate(research.stay.candidates, 1):
        offer, deal = candidate.offer, _stay_deal(candidate, request.currency)
        proof = evidence(offer.name, deal.url)
        pid = place(
            f"hotel_{i}",
            offer.name,
            "HOTEL",
            latitude=offer.latitude,
            longitude=offer.longitude,
            provider_id=offer.id,
            proof=proof,
        )
        cid = cost(
            f"cost_stay_{i}",
            "ACCOMMODATION",
            f"{offer.name} · {candidate.rooms} rooms · whole stay",
            deal.currency,
            deal.total_minor,
            proof,
        )
        oid = f"stay_option_{i}"
        if offer.id == research.stay.recommended_id:
            stay_selection = oid
        stay_options.append(
            {
                "id": oid,
                "placeId": pid,
                "title": offer.name,
                "reason": research.stay.rationale,
                "roomType": None,
                "roomCount": candidate.rooms,
                "guestCapacity": None,
                "amenities": offer.amenities,
                "availability": "UNKNOWN",
                "cancellationSummary": (
                    "Free cancellation reported; verify terms." if deal.free_cancellation else None
                ),
                "costItemIds": [cid],
                "evidence": proof,
            }
        )
    if stay_selection is None:
        raise ValueError("Selected accommodation is missing.")
    stays = [
        {
            "id": "stay",
            "title": f"Stay in {request.destination}",
            "checkInDate": research.check_in.isoformat(),
            "checkOutDate": research.check_out.isoformat(),
            "nights": (research.check_out - research.check_in).days,
            "status": "AVAILABLE",
            "recommendedOptionId": stay_selection,
            "planningOptionId": stay_selection,
            "userSelectedOptionId": None,
            "options": stay_options,
        }
    ]
    place_ids = {}
    for i, (label, item) in enumerate(context.places_by_label.items(), 1):
        place_ids[label] = place(
            f"place_{i}",
            item.source_title or "Place name unavailable",
            "OTHER",
            latitude=item.latitude,
            longitude=item.longitude,
            provider_id=item.place_id,
            proof=evidence(item.source_title or "Google place", item.google_maps_url),
        )

    first_date, last_date = selected.outbound.departure.date(), selected.inbound.arrival.date()
    count = (last_date - first_date).days + 1
    if not 1 <= count <= 60:
        raise ItineraryCompatibilityError(
            "Full journey, including arrival home, must fit the 60-day contract limit."
        )
    if research.check_in < first_date or research.check_out > last_date:
        raise ItineraryCompatibilityError(
            "This journey crosses the date line with reversed local calendar dates; "
            "the current timeline cannot represent it safely."
        )
    days = []
    for i in range(count):
        day_date = first_date + timedelta(days=i)
        zone = destination_zone
        if day_date < research.check_in:
            zone = origin_zone
        elif day_date > research.check_out:
            zone = selected.inbound.segments[-1].arrival_tz
        days.append(
            {
                "id": f"day_{i + 1}",
                "dayNumber": i + 1,
                "date": day_date.isoformat(),
                "timeZone": zone,
                "title": f"Day {i + 1}",
                "summary": "Estimated plan; transfers and opening hours require verification.",
                "walkingDistanceMeters": None,
                "events": [],
                "continuingEventIds": [],
            }
        )
    by_date = {day["date"]: day for day in days}
    intervals = {}
    for direction in ("outbound", "inbound"):
        sector = getattr(selected, direction)
        event_id = f"event_{direction}"
        by_date[sector.departure.date().isoformat()]["events"].append(
            {
                "id": event_id,
                "type": "TRANSPORT",
                "title": f"{direction.title()} flights",
                "description": research.transport.rationale,
                "imageIds": [],
                "transportGroupId": direction,
            }
        )
        intervals[event_id] = (sector.departure, sector.arrival)
    for day in context.schedule.days:
        for j, item in enumerate(day.items, 1):
            start = datetime.combine(day.date, item.start_time, ZoneInfo(destination_zone))
            end = datetime.combine(day.date, item.end_time, ZoneInfo(destination_zone))
            event = {
                "id": f"event_{day.date.isoformat()}_{j}",
                "type": item.type,
                "title": item.title,
                "description": item.description,
                "imageIds": [],
                "start": moment(start, destination_zone),
                "end": moment(end, destination_zone),
                "timingStatus": "ESTIMATED",
                "evidence": dict(unverified),
                "placeId": place_ids.get(item.place_label),
                "costItemIds": [],
            }
            if item.type == "ACTIVITY":
                event["details"] = {
                    "category": "OTHER",
                    "reservationStatus": "UNKNOWN",
                    "openingHoursNote": None,
                }
            elif item.type == "MEAL":
                event["details"] = {
                    "mealType": "UNKNOWN",
                    "cuisine": "Not verified",
                    "dietaryStatus": "UNVERIFIED",
                    "reservationStatus": "UNKNOWN",
                }
            by_date[day.date.isoformat()]["events"].append(event)
            intervals[event["id"]] = (start, end)
    for day in days:
        day["events"].sort(key=lambda e: intervals[e["id"]][0].timestamp())
        boundary = datetime.fromisoformat(day["date"]).replace(tzinfo=ZoneInfo(day["timeZone"]))
        for event_id in ("event_outbound", "event_inbound"):
            start, end = intervals[event_id]
            if start.date().isoformat() < day["date"] and end > boundary:
                day["continuingEventIds"].append(event_id)

    active = [
        flight_costs[selected.id],
        next(o for o in stay_options if o["id"] == stay_selection)["costItemIds"][0],
    ]
    subtotal = sum(
        c["displayTotal"]["expectedMinor"]
        for c in costs
        if c["id"] in active and c["displayTotal"] is not None
    )
    currencies = sorted({request.currency} | {c["originalTotal"]["currency"] for c in costs})
    constraints = [
        {"id": f"constraint_{i}", "code": "CUSTOM", "strength": "UNSPECIFIED", "description": value}
        for i, value in enumerate(request.constraints, 1)
    ]
    notes = [f"Budget as entered: {request.budget}", f"Companions as entered: {request.companion}"]
    notes += [f"Step {k}: {v}" for k, v in request.notes.items()]
    caveats = [
        (
            "Partial quote subtotal only; meals, activities, local transfers "
            "and unverified fees are excluded."
        ),
        "No exchange rates or contingency allowance have been applied.",
        (
            "Accommodation room type/capacity, entry requirements "
            "and self-transfer status are unverified."
        ),
    ]
    if len(candidates) != len(research.transport.candidates):
        caveats.append(
            "Flight alternatives using different endpoint airports "
            "are not included in these comparison groups."
        )
    itinerary = {
        "contractId": CONTRACT_ID,
        "schemaVersion": "2.0",
        "id": itinerary_id or str(uuid4()),
        "revision": 1,
        "language": "en",
        "dataMode": "LIVE",
        "status": "PARTIAL",
        "generatedAt": stamp,
        "requestSnapshot": {
            "originPlaceId": "origin",
            "destinationPlaceIds": ["destination"],
            "dateMode": "FIXED",
            "requestedStartDate": request.startDate.isoformat(),
            "requestedEndDate": request.endDate.isoformat(),
            "returnDateMeaning": "LEAVE_DESTINATION",
            "companions": "CUSTOM",
            "travelers": [
                {
                    "id": f"traveler_{i}",
                    "age": None,
                    "passportCountry": None,
                    "passportType": None,
                    "residenceCountry": None,
                }
                for i in range(1, request.adults + 1)
            ],
            "budget": {
                "scope": "ENTIRE_PARTY_WHOLE_TRIP",
                "target": None,
                "includeIntercityTransport": None,
            },
            "constraints": constraints,
            "notes": notes,
        },
        "summary": {
            "title": f"{request.departure} → {request.destination}",
            "subtitle": context.schedule.rationale,
            "startDate": first_date.isoformat(),
            "endDate": last_date.isoformat(),
            "dayCount": len(days),
            "coverImageId": None,
            "highlights": [],
            "advice": caveats,
        },
        "places": places,
        "media": [],
        "transportGroups": groups,
        "stayGroups": stays,
        "days": days,
        "money": {
            "displayCurrency": request.currency,
            "currencies": [
                {"code": c, "minorUnitDigits": minor_unit_digits(c)} for c in currencies
            ],
            "exchangeRates": [],
            "exchangeAdvice": [],
        },
        "entryRequirements": [],
        "budget": {
            "scope": "ENTIRE_PARTY_WHOLE_TRIP",
            "lineItems": costs,
            "tripCostItemIds": [],
            "activeCostItemIds": active,
            "completeness": "PARTIAL",
            "subtotal": price_range(request.currency, subtotal),
            "contingency": price_range(request.currency, 0),
            "total": price_range(request.currency, subtotal),
            "excludedCategories": [
                "FOOD",
                "ACTIVITIES",
                "LOCAL_TRANSPORT",
                "DOCUMENTS",
                "Unverified fees",
            ],
            "notes": caveats
            + [
                (
                    "Foreign-currency quotes retain their original totals "
                    "and are excluded from the display subtotal."
                )
            ],
        },
        "quality": {
            "sources": sources,
            "assumptions": [],
            "notices": [
                {
                    "code": "PARTIAL_RESEARCH",
                    "severity": "WARNING",
                    "message": (
                        "Some facts and costs remain unverified; no bookings have been made."
                    ),
                    "relatedIds": [],
                }
            ],
            "constraintChecks": [
                {
                    "constraintId": c["id"],
                    "status": "UNVERIFIED",
                    "explanation": "Not independently verified.",
                    "relatedIds": [],
                }
                for c in constraints
            ],
        },
    }
    validate_itinerary(itinerary)
    return itinerary
