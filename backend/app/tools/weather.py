"""Geocoding and daily weather from Open-Meteo (no API key).

Near dates get a real forecast. Dates beyond the forecast horizon get "typical" weather:
the average of the same calendar dates over the previous years, clearly labelled as such.
"""

import asyncio
from collections import Counter
from datetime import date, timedelta
from statistics import mean
from typing import Literal

import httpx
from pydantic import BaseModel

from app.tools.http import get_json
from app.tools.models import ToolError

GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"
FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
ARCHIVE_URL = "https://archive-api.open-meteo.com/v1/archive"

FORECAST_HORIZON_DAYS = 14  # Open-Meteo allows 16; keep a margin for time-zone edges
TYPICAL_YEARS = 3
RAIN_DAY_MM = 1.0
_DAILY = "temperature_2m_max,temperature_2m_min,precipitation_sum,weather_code"

_WMO = {
    0: "clear sky", 1: "mainly clear", 2: "partly cloudy", 3: "overcast",
    45: "fog", 48: "freezing fog",
    51: "light drizzle", 53: "drizzle", 55: "heavy drizzle", 56: "freezing drizzle",
    57: "heavy freezing drizzle",
    61: "light rain", 63: "rain", 65: "heavy rain", 66: "freezing rain", 67: "heavy freezing rain",
    71: "light snow", 73: "snow", 75: "heavy snow", 77: "snow grains",
    80: "light showers", 81: "showers", 82: "violent showers",
    85: "light snow showers", 86: "heavy snow showers",
    95: "thunderstorm", 96: "thunderstorm with hail", 99: "severe thunderstorm with hail",
}  # fmt: skip


class GeoPlace(BaseModel):
    name: str
    admin1: str | None  # state / prefecture
    country: str
    country_code: str
    latitude: float
    longitude: float
    timezone: str
    population: int | None


class WeatherDay(BaseModel):
    date: date
    kind: Literal["forecast", "typical"]
    temp_max_c: float | None
    temp_min_c: float | None
    precip_mm: float | None
    precip_probability_pct: int | None  # forecast only
    rainy_day_share: float | None  # typical only: share of past years with >= 1 mm
    summary: str
    years_sampled: int | None  # typical only


async def geocode(client: httpx.AsyncClient, query: str, count: int = 10) -> list[GeoPlace]:
    """Places matching "Kyoto" or "Kyoto, Japan" (text after the comma must match the region
    or country, so an ambiguous name never silently resolves to the wrong place)."""
    name, _, hint = (part.strip() for part in query.partition(","))
    if not name:
        raise ToolError("empty place name")
    data = await get_json(
        client, GEOCODING_URL, {"name": name, "count": count, "language": "en", "format": "json"}
    )
    places = [
        GeoPlace(
            name=r["name"],
            admin1=r.get("admin1"),
            country=r.get("country", ""),
            country_code=r.get("country_code", ""),
            latitude=r["latitude"],
            longitude=r["longitude"],
            timezone=r.get("timezone", "UTC"),
            population=r.get("population"),
        )
        for r in data.get("results") or []
    ]
    if hint:
        h = hint.lower()
        places = [
            p
            for p in places
            if h in {(p.country or "").lower(), p.country_code.lower(), (p.admin1 or "").lower()}
        ]
    return places


def _describe(code: int | None) -> str:
    return _WMO.get(code, "unknown") if code is not None else "unknown"


def _rows(payload: dict) -> list[dict]:
    daily = payload.get("daily")
    if not daily or "time" not in daily:
        raise ToolError(f"Open-Meteo reply has no daily data: {str(payload)[:200]}")
    keys = [k for k in daily if k != "time"]
    return [
        {"date": date.fromisoformat(t), **{k: daily[k][i] for k in keys}}
        for i, t in enumerate(daily["time"])
    ]


async def _forecast(
    client: httpx.AsyncClient, lat: float, lon: float, start: date, end: date
) -> list[WeatherDay]:
    payload = await get_json(
        client,
        FORECAST_URL,
        {
            "latitude": lat,
            "longitude": lon,
            "daily": _DAILY + ",precipitation_probability_max",
            "timezone": "auto",
            "start_date": start.isoformat(),
            "end_date": end.isoformat(),
        },
    )
    return [
        WeatherDay(
            date=r["date"],
            kind="forecast",
            temp_max_c=r.get("temperature_2m_max"),
            temp_min_c=r.get("temperature_2m_min"),
            precip_mm=r.get("precipitation_sum"),
            precip_probability_pct=r.get("precipitation_probability_max"),
            rainy_day_share=None,
            summary=_describe(r.get("weather_code")),
            years_sampled=None,
        )
        for r in _rows(payload)
    ]


def _years_back(day: date, years: int) -> date:
    try:
        return day.replace(year=day.year - years)
    except ValueError:  # 29 Feb
        return date(day.year - years, 2, 28)


async def _history(
    client: httpx.AsyncClient, lat: float, lon: float, start: date, end: date, years_back: int
) -> list[dict]:
    first = _years_back(start, years_back)
    payload = await get_json(
        client,
        ARCHIVE_URL,
        {
            "latitude": lat,
            "longitude": lon,
            "daily": _DAILY,
            "timezone": "auto",
            "start_date": first.isoformat(),
            "end_date": (first + (end - start)).isoformat(),
        },
    )
    return _rows(payload)


async def _typical(
    client: httpx.AsyncClient, lat: float, lon: float, start: date, end: date
) -> list[WeatherDay]:
    years = await asyncio.gather(
        *(_history(client, lat, lon, start, end, k) for k in range(1, TYPICAL_YEARS + 1))
    )
    days = []
    for offset in range(1, (end - start).days + 2):
        samples = [rows[offset - 1] for rows in years if offset - 1 < len(rows)]
        tmax = [s["temperature_2m_max"] for s in samples if s["temperature_2m_max"] is not None]
        tmin = [s["temperature_2m_min"] for s in samples if s["temperature_2m_min"] is not None]
        rain = [s["precipitation_sum"] for s in samples if s["precipitation_sum"] is not None]
        codes = [s["weather_code"] for s in samples if s["weather_code"] is not None]
        if not (tmax and tmin and rain):
            raise ToolError(f"no historical weather data for {start + timedelta(offset - 1)}")
        days.append(
            WeatherDay(
                date=start + timedelta(days=offset - 1),
                kind="typical",
                temp_max_c=round(mean(tmax), 1),
                temp_min_c=round(mean(tmin), 1),
                precip_mm=round(mean(rain), 1),
                precip_probability_pct=None,
                rainy_day_share=round(sum(r >= RAIN_DAY_MM for r in rain) / len(rain), 2),
                summary=_describe(Counter(codes).most_common(1)[0][0] if codes else None),
                years_sampled=len(rain),
            )
        )
    return days


async def get_weather(
    client: httpx.AsyncClient,
    latitude: float,
    longitude: float,
    start: date,
    end: date,
    *,
    today: date | None = None,
) -> list[WeatherDay]:
    """One entry per day in [start, end]: forecast where available, typical weather otherwise."""
    today = today or date.today()
    if end < start:
        raise ToolError("end date is before start date")
    if start < today:
        raise ToolError(f"{start} is in the past")
    if (end - start).days > 60:
        raise ToolError("weather range is limited to 61 days")
    horizon = today + timedelta(days=FORECAST_HORIZON_DAYS)
    tasks = []
    if start <= horizon:
        tasks.append(_forecast(client, latitude, longitude, start, min(end, horizon)))
    if end > horizon:
        tasks.append(_typical(client, latitude, longitude, max(start, horizon + timedelta(1)), end))
    return [day for part in await asyncio.gather(*tasks) for day in part]
