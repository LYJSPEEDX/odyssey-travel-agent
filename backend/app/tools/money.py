from decimal import ROUND_HALF_UP, Decimal

# ISO 4217 currencies whose minor unit is not 1/100. Everything else defaults to 2 digits.
_ZERO_DIGIT = {
    "BIF",
    "CLP",
    "DJF",
    "GNF",
    "ISK",
    "JPY",
    "KMF",
    "KRW",
    "PYG",
    "RWF",
    "UGX",
    "UYI",
    "VND",
    "VUV",
    "XAF",
    "XOF",
    "XPF",
}
_THREE_DIGIT = {"BHD", "IQD", "JOD", "KWD", "LYD", "OMR", "TND"}


def minor_unit_digits(currency: str) -> int:
    code = currency.upper()
    if code in _ZERO_DIGIT:
        return 0
    if code in _THREE_DIGIT:
        return 3
    return 2


def to_minor_units(amount: float | int | str | Decimal, currency: str) -> int:
    """Convert a major-unit amount to integer minor units (the itinerary protocol's money form)."""
    scale = Decimal(10) ** minor_unit_digits(currency)
    return int((Decimal(str(amount)) * scale).quantize(Decimal(1), rounding=ROUND_HALF_UP))
