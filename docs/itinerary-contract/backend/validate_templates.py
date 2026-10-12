"""Stdlib-only shape validation; scalar nulls are intentional blank placeholders."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SCHEMA = json.loads((ROOT.parent / "itinerary.schema.json").read_text())


def check(value, node, path):
    if "$ref" in node:
        node = SCHEMA["$defs"][node["$ref"].split("/")[-1]]
    if "oneOf" in node or "anyOf" in node:
        branches = node.get("oneOf", node.get("anyOf"))
        for branch in branches:
            try:
                check(value, branch, path)
                return
            except AssertionError:
                pass
        raise AssertionError(f"{path}: no matching shape")
    if value is None:
        assert node.get("type") not in ("object", "array"), f"{path}: missing structure"
        assert "const" not in node, f"{path}: missing constant"
        return
    if "const" in node:
        assert value == node["const"], f"{path}: constant mismatch"
    if "enum" in node:
        assert value in node["enum"], f"{path}: enum mismatch"
    kind = node.get("type")
    if kind == "object":
        assert isinstance(value, dict), f"{path}: expected object"
        assert set(value) <= set(node["properties"]), f"{path}: extra keys"
        assert set(node.get("required", [])) <= set(value), f"{path}: missing keys"
        for key, item in value.items():
            check(item, node["properties"][key], f"{path}.{key}")
    elif kind == "array":
        assert isinstance(value, list), f"{path}: expected array"
        for i, item in enumerate(value):
            check(item, node["items"], f"{path}[{i}]")
    elif kind == "string":
        assert isinstance(value, str) and value, f"{path}: expected nonempty string"
    elif kind == "integer":
        assert type(value) is int, f"{path}: expected integer"
    elif kind == "number":
        assert type(value) in (int, float), f"{path}: expected number"
    elif kind == "boolean":
        assert type(value) is bool, f"{path}: expected boolean"
    elif kind == "null":
        raise AssertionError(f"{path}: expected null")


def main():
    response = json.loads((ROOT / "response.template.json").read_text())
    variants = json.loads((ROOT / "variants.template.json").read_text())
    check(response, SCHEMA, "response")
    assert set(response) == set(SCHEMA["properties"])
    assert set(variants) == {"events", "transportLegs", "structures"}
    events = SCHEMA["$defs"]["Event"]["oneOf"]
    assert set(variants["events"]) == {b["properties"]["type"]["const"] for b in events}
    for branch in events:
        name = branch["properties"]["type"]["const"]
        check(variants["events"][name], branch, f"events.{name}")
    modes = {"FLIGHT", "DRIVE", "BUS", "TRAIN", "FERRY", "TAXI", "WALK"}
    assert set(variants["transportLegs"]) == modes
    for mode, value in variants["transportLegs"].items():
        assert value["mode"] == mode
        check(value, SCHEMA["$defs"]["TransportLeg"], f"transportLegs.{mode}")
    definitions = set(SCHEMA["$defs"]) - {"Event", "TransportLeg"}
    assert set(variants["structures"]) == definitions
    for name, value in variants["structures"].items():
        check(value, SCHEMA["$defs"][name], f"structures.{name}")
    print(f"PASS: 2 JSON templates; {len(response)} top-level keys; 5 events; 7 modes; {len(definitions)} shared definitions. Scalar null placeholders intentionally exempted.")


if __name__ == "__main__":
    main()
