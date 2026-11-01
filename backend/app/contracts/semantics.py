"""Semantic validation shared by generated itineraries and design fixtures."""

from datetime import date, datetime, time, timedelta
from decimal import ROUND_HALF_UP, Decimal
from zoneinfo import ZoneInfo

RANGE_KEYS = ("lowMinor", "expectedMinor", "highMinor")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def nodes(value):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from nodes(child)
    elif isinstance(value, list):
        for child in value:
            yield from nodes(child)


def instant(moment):
    stamp = datetime.fromisoformat(moment["at"].replace("Z", "+00:00"))
    require(
        stamp.utcoffset() == stamp.astimezone(ZoneInfo(moment["timeZone"])).utcoffset(),
        "Timestamp offset does not match IANA time zone",
    )
    return stamp


def validate_semantics(trip, validator):
    validator.validate(trip)
    all_nodes = list(nodes(trip))
    all_ids = [n["id"] for n in all_nodes if "id" in n]
    require(len(all_ids) == len(set(all_ids)), "IDs must be globally unique within one itinerary")
    id_set = set(all_ids)
    places = {x["id"]: x for x in trip["places"]}
    media = {x["id"]: x for x in trip["media"]}
    sources = {x["id"]: x for x in trip["quality"]["sources"]}
    costs = {x["id"]: x for x in trip["budget"]["lineItems"]}
    transports = {x["id"]: x for x in trip["transportGroups"]}
    stays = {x["id"]: x for x in trip["stayGroups"]}
    travelers = {x["id"]: x for x in trip["requestSnapshot"]["travelers"]}
    constraints = {x["id"]: x for x in trip["requestSnapshot"]["constraints"]}
    events = {e["id"]: e for d in trip["days"] for e in d["events"]}
    currencies = {c["code"]: c["minorUnitDigits"] for c in trip["money"]["currencies"]}
    rates = {r["id"]: r for r in trip["money"]["exchangeRates"]}
    display = trip["money"]["displayCurrency"]
    require(display in currencies, "Display currency metadata missing")
    require(len(currencies) == len(trip["money"]["currencies"]), "Duplicate currency metadata")

    def ref(value, collection, label):
        require(value is None or value in collection, "Broken " + label + ": " + str(value))

    for n in all_nodes:
        for key in ("placeId", "fromPlaceId", "toPlaceId", "originPlaceId"):
            if key in n:
                ref(n[key], places, key)
        for key in ("destinationPlaceIds", "restStopPlaceIds"):
            for value in n.get(key, []):
                ref(value, places, key)
        for value in n.get("imageIds", []):
            ref(value, media, "imageId")
        for value in n.get("sourceIds", []):
            ref(value, sources, "sourceId")
        for value in n.get("costItemIds", []):
            ref(value, costs, "costItemId")
        for value in n.get("relatedIds", []):
            ref(value, id_set, "relatedId")
        if "evidence" in n:
            evidence = n["evidence"]
            if evidence["status"] == "SOURCED":
                require(
                    evidence["sourceIds"] and evidence["checkedAt"], "SOURCED requires evidence"
                )
            if trip["dataMode"] == "MOCK":
                require(evidence["status"] == "MOCK", "Mock example claims live evidence")
        if "at" in n and "timeZone" in n:
            instant(n)
        if set(RANGE_KEYS).issubset(n):
            require(n["lowMinor"] <= n["expectedMinor"] <= n["highMinor"], "Invalid money range")
            ref(n["currency"], currencies, "currency")
    ref(trip["summary"]["coverImageId"], media, "coverImageId")
    for p in places.values():
        if p["timeZone"] is not None:
            ZoneInfo(p["timeZone"])
    for asset in media.values():
        if asset["status"] == "READY":
            require(
                asset["url"] or (asset["provider"] == "OWNED" and asset["assetKey"]),
                "Ready image has no resolvable location",
            )
            require(asset["provider"] != "NONE", "Ready image has no provider")
        if asset["expiresAt"]:
            require(
                asset["status"] != "READY"
                or datetime.fromisoformat(asset["expiresAt"])
                > datetime.fromisoformat(trip["generatedAt"]),
                "Ready image already expired at generation",
            )

    def selected(g, hotel=False):
        options = {o["id"]: o for o in g["options"]}
        for key in ("recommendedOptionId", "planningOptionId", "userSelectedOptionId"):
            ref(g[key], options, key)
        if g["status"] == "AVAILABLE":
            require(len(options) >= 1, "Available group lacks required options")
            require(g["planningOptionId"] is not None, "Available group has no working option")
        else:
            require(
                not options
                and all(
                    g[k] is None
                    for k in ("recommendedOptionId", "planningOptionId", "userSelectedOptionId")
                ),
                "Pending/unavailable group must not claim a choice",
            )
        return options.get(g["planningOptionId"])

    for g in transports.values():
        selected(g)
        for o in g["options"]:
            legs = o["legs"]
            require(
                legs[0]["fromPlaceId"] == g["fromPlaceId"]
                and legs[-1]["toPlaceId"] == g["toPlaceId"],
                "Option endpoints differ from group",
            )
            prev = None
            for leg in legs:
                start, end = instant(leg["departure"]), instant(leg["arrival"])
                require(
                    end > start and (end - start).total_seconds() == leg["durationMinutes"] * 60,
                    "Incorrect leg duration or end time",
                )
                if prev:
                    require(
                        prev["toPlaceId"] == leg["fromPlaceId"]
                        and instant(prev["arrival"]) <= start,
                        "Disconnected or overlapping transport legs",
                    )
                prev = leg
    for g in stays.values():
        selected(g, hotel=True)
        require(
            (date.fromisoformat(g["checkOutDate"]) - date.fromisoformat(g["checkInDate"])).days
            == g["nights"],
            "Incorrect hotel night count",
        )
        for o in g["options"]:
            require(
                o["guestCapacity"] is None or o["guestCapacity"] >= len(travelers),
                "Insufficient total room capacity",
            )

    active = set(trip["budget"]["tripCostItemIds"])
    for g in stays.values():
        option = selected(g, hotel=True)
        if option:
            active.update(option["costItemIds"])
    for cost_id in active:
        ref(cost_id, costs, "costItemId")
    event_intervals = {}
    last_end = None
    used_groups = set()
    start_date = date.fromisoformat(trip["summary"]["startDate"])
    require(trip["summary"]["dayCount"] == len(trip["days"]), "Day count mismatch")
    require(trip["summary"]["endDate"] == trip["days"][-1]["date"], "End date mismatch")
    for i, day in enumerate(trip["days"]):
        require(day["dayNumber"] == i + 1, "Day numbers must be sequential")
        require(
            date.fromisoformat(day["date"]) == start_date + timedelta(days=i),
            "Missing calendar day",
        )
        ZoneInfo(day["timeZone"])
        for e in day["events"]:
            if e["type"] == "TRANSPORT":
                ref(e["transportGroupId"], transports, "transportGroupId")
                require(e["transportGroupId"] not in used_groups, "Transport group scheduled twice")
                used_groups.add(e["transportGroupId"])
                option = selected(transports[e["transportGroupId"]])
                if option is None:
                    require(trip["status"] != "READY", "Ready timeline has unresolved transport")
                    continue
                start = instant(option["legs"][0]["departure"])
                end = instant(option["legs"][-1]["arrival"])
                active.update(option["costItemIds"])
            else:
                start, end = instant(e["start"]), instant(e["end"])
                active.update(e.get("costItemIds", []))
                if e["type"] == "STAY":
                    ref(e["stayGroupId"], stays, "stayGroupId")
                    stay = stays[e["stayGroupId"]]
                    require(
                        start.date().isoformat()
                        == stay["checkInDate" if e["action"] == "CHECK_IN" else "checkOutDate"],
                        "Stay event date mismatch",
                    )
            require(end > start, "Event end must follow start")
            require(
                start.date().isoformat() == day["date"],
                "Event belongs to wrong departure-local day",
            )
            require(last_end is None or start >= last_end, "Timeline overlaps in absolute time")
            last_end = end
            event_intervals[e["id"]] = (start, end)
        for event_id in day["continuingEventIds"]:
            require(
                event_id in event_intervals and events[event_id]["type"] == "TRANSPORT",
                "Continuation must refer to an earlier transport event",
            )
            start, end = event_intervals[event_id]
            boundary = datetime.combine(
                date.fromisoformat(day["date"]), time(), ZoneInfo(day["timeZone"])
            )
            require(
                start < boundary + timedelta(days=1) and end > boundary,
                "Continuation does not intersect this day",
            )

    for check in trip["quality"]["constraintChecks"]:
        ref(check["constraintId"], constraints, "constraintId")
    for req in trip["entryRequirements"]:
        for value in req["travelerIds"]:
            ref(value, travelers, "travelerId")
        if req["status"] == "NEEDS_INPUT":
            require(req["requiredInputFields"], "Missing entry questions")
    for rate in rates.values():
        require(Decimal(rate["quotePerBase"]) > 0, "Invalid exchange rate")
        ref(rate["baseCurrency"], currencies, "baseCurrency")
        ref(rate["quoteCurrency"], currencies, "quoteCurrency")
    for line in costs.values():
        unit, original, converted = line["unitPrice"], line["originalTotal"], line["displayTotal"]
        if unit is None:
            require(original is None and converted is None, "Unknown price must not become zero")
            continue
        require(original is not None, "Known unit price requires original total")
        require(unit["currency"] == original["currency"], "Original currency mismatch")
        require(
            all(original[k] == unit[k] * line["quantity"] for k in RANGE_KEYS),
            "Quantity calculation wrong",
        )
        if original["currency"] == display:
            require(
                line["exchangeRateId"] is None and converted == original,
                "Unnecessary currency conversion",
            )
        elif converted is not None:
            ref(line["exchangeRateId"], rates, "exchangeRateId")
            require(line["exchangeRateId"] is not None, "Conversion needs an exchange rate")
            rate = rates[line["exchangeRateId"]]
            require(
                rate["baseCurrency"] == original["currency"] and rate["quoteCurrency"] == display,
                "Exchange rate direction wrong",
            )
            factor = Decimal(rate["quotePerBase"]) * Decimal(10) ** (
                currencies[display] - currencies[original["currency"]]
            )
            require(converted["currency"] == display, "Display currency mismatch")
            for k in RANGE_KEYS:
                require(
                    converted[k]
                    == int(
                        (Decimal(original[k]) * factor).quantize(Decimal(1), rounding=ROUND_HALF_UP)
                    ),
                    "Conversion calculation wrong",
                )
    budget = trip["budget"]
    require(set(budget["activeCostItemIds"]) == active, "Active cost IDs differ from selected plan")
    known = [costs[x]["displayTotal"] for x in active if costs[x]["displayTotal"] is not None]
    if budget["completeness"] == "COMPLETE":
        require(
            len(known) == len(active)
            and not budget["excludedCategories"]
            and all(costs[x]["taxesAndFees"] == "INCLUDED" for x in active),
            "Incomplete prices advertised as complete",
        )
    for section in ("subtotal", "contingency", "total"):
        require(budget[section]["currency"] == display, "Budget display currency mismatch")
    for k in RANGE_KEYS:
        require(budget["subtotal"][k] == sum(x[k] for x in known), "Subtotal calculation wrong")
        require(
            budget["total"][k] == budget["subtotal"][k] + budget["contingency"][k],
            "Total calculation wrong",
        )
