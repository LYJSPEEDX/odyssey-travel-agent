"""Validate the design fixtures, not live travel facts. Requires jsonschema >= 4."""

import copy
import json
import sys
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker
from jsonschema.exceptions import ValidationError

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT.parents[1] / "backend"))
from app.contracts.semantics import require, validate_semantics


def validate(trip):
    schema_name = (
        "frontend-itinerary-v2.schema.json"
        if trip.get("schemaVersion") == "2.0"
        else "itinerary.schema.json"
    )
    schema = json.loads((ROOT / schema_name).read_text())
    validate_semantics(
        trip, Draft202012Validator(schema, format_checker=FormatChecker())
    )


def main():
    fixtures = [
        json.loads(p.read_text()) for p in sorted((ROOT / "examples").glob("*.json"))
    ]
    require(bool(fixtures), "No fixtures found")
    for trip in fixtures:
        validate(trip)
        print(
            "PASS",
            trip["id"],
            "days=",
            len(trip["days"]),
            "events=",
            sum(len(d["events"]) for d in trip["days"]),
            "total=",
            trip["budget"]["total"],
        )
    original = next(t for t in fixtures if t["schemaVersion"] == "1.0")
    mutations = []

    def bad(label, mutate):
        fixture = copy.deepcopy(original)
        mutate(fixture)
        mutations.append((label, fixture))

    bad(
        "wrong transport payload",
        lambda t: t["transportGroups"][0]["options"][0]["legs"][0].update(
            mode="FLIGHT"
        ),
    )
    bad("unknown field", lambda t: t.update(inventedField=True))
    bad("broken image reference", lambda t: t["summary"].update(coverImageId="missing"))
    bad("wrong day count", lambda t: t["summary"].update(dayCount=10))
    bad(
        "unselected hotel charged",
        lambda t: t["budget"]["activeCostItemIds"].append("cost_hotel_2"),
    )
    bad("wrong total", lambda t: t["budget"]["total"].update(expectedMinor=1))
    bad(
        "wrong leg duration",
        lambda t: t["transportGroups"][0]["options"][0]["legs"][0].update(
            durationMinutes=1
        ),
    )
    bad("ready image without location", lambda t: t["media"][0].update(status="READY"))
    for label, fixture in mutations:
        try:
            validate(fixture)
        except (ValueError, ValidationError):
            print("REJECT", label)
        else:
            raise AssertionError("Invalid fixture accepted: " + label)
    print(
        f"PASS schemas + {len(fixtures)} examples + {len(mutations)} rejection checks"
    )


if __name__ == "__main__":
    main()
