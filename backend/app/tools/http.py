"""Shared JSON-over-HTTP GET for the plain REST data sources."""

from typing import Any

import httpx

from app.tools.models import ToolError

DEFAULT_TIMEOUT = 20.0


def make_client(timeout: float = DEFAULT_TIMEOUT) -> httpx.AsyncClient:
    return httpx.AsyncClient(timeout=timeout, headers={"user-agent": "odyssey-backend/0.1"})


def _reason(response: httpx.Response) -> str:
    try:
        body = response.json()
    except ValueError:
        return response.text[:200]
    if isinstance(body, dict):
        return str(body.get("reason") or body.get("message") or body)[:200]
    return str(body)[:200]


async def get_json(client: httpx.AsyncClient, url: str, params: dict | None = None) -> Any:
    """GET and decode JSON. One retry on transport errors / 5xx; 4xx fail immediately."""
    last: str = ""
    for _ in range(2):
        try:
            response = await client.get(url, params=params)
        except httpx.TransportError as exc:
            last = f"{type(exc).__name__}: {exc}"
            continue
        if response.status_code >= 500:
            last = f"HTTP {response.status_code}"
            continue
        if response.status_code >= 400:
            raise ToolError(f"{url} -> HTTP {response.status_code}: {_reason(response)}")
        try:
            return response.json()
        except ValueError as exc:
            raise ToolError(f"{url} returned non-JSON data") from exc
    raise ToolError(f"{url} unavailable ({last})")
