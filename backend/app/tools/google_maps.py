from pydantic import BaseModel

from app.tools.place_models import PlaceInfo
import json

from app.tools.mcp import McpClient
from app.tools.models import ToolError
from app.tools.place_models import parse_place


class PlaceSearchResult(BaseModel):
    """Keep the search summary alongside its referenced places."""

    summary: str
    places: list[PlaceInfo]

async def search_places(
        client: McpClient,
        query: str,
) -> PlaceSearchResult:
    """Search Google Maps and normalize the returned place records."""
    result = await client.call_tool(
        "search_places",
        {"textQuery": query},
    )

    if result.is_error:
        raise ToolError("Google Maps place search returned an error.")

    data = result.structured

    if data is None:
        try:
            data = json.loads(result.text)
        except json.JSONDecodeError as exc:
            raise ToolError("Google Maps returned invalid JSON.") from exc

    if not isinstance(data, dict):
        raise ToolError("Google Maps returned an unexpected response structure.")

    raw_places = data.get("places", [])
    summary = data.get("summary", "")

    if not isinstance(raw_places, list):
        raise ToolError("Google Maps returned an invalid places field.")

    if not isinstance(summary, str):
        raise ToolError("Google Maps returned an invalid summary field.")

    try:
        places = [parse_place(item) for item in raw_places]
    except (KeyError, TypeError, AttributeError, ValueError) as exc:
        raise ToolError("Google Maps returned an invalid place record.") from exc

    return PlaceSearchResult(
        summary=summary,
        places=places,
    )