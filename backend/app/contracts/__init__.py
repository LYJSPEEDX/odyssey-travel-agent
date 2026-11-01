"""Packaged canonical itinerary contract and explicit legacy discrimination."""

import json
from functools import lru_cache
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker

from .semantics import validate_semantics

CONTRACT_ID = "odyssey.frontend.itinerary"


@lru_cache
def validator():
    schema = json.loads(Path(__file__).with_name("frontend-itinerary-v2.schema.json").read_text())
    Draft202012Validator.check_schema(schema)
    return Draft202012Validator(schema, format_checker=FormatChecker())


def validate_itinerary(document):
    validate_semantics(document, validator())


def is_legacy_backend(document):
    return document.get("schemaVersion") == "2.0" and document.get("contractId") != CONTRACT_ID
