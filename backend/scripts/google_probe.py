"""Inspect Google Maps Grounding Lite tool schemas and real place-search responses.

Run from backend/:
    uv run python -m scripts.google_probe
    uv run python -m scripts.google_probe "parks in Kyoto, Japan"
"""

import argparse
import asyncio
import json
import sys

import httpx
from pydantic import ValidationError

from app.config import get_settings
from app.tools.mcp import McpClient, McpError


def safe_print(message: str, api_key: str) -> None:
    """Print diagnostic output without exposing the configured API key."""
    print(message.replace(api_key, "[REDACTED]"))


async def list_tools(client: McpClient, api_key: str) -> int:
    """Print live tool descriptions and input schemas from an initialized client."""
    tools = await client.list_tools()
    if not tools:
        print("Connected, but the server returned no available tools.")
        return 1

    print(f"Connected successfully. Found {len(tools)} tools.")
    for index, tool in enumerate(tools, start=1):
        name = str(tool.get("name", "(unnamed)"))
        description = str(tool.get("description", ""))
        schema = json.dumps(tool.get("inputSchema", {}), ensure_ascii=False, indent=2)
        safe_print(f"\n[{index}] {name}\n{description}\nInput schema:\n{schema}", api_key)
    return 0


async def search_places(client: McpClient, query: str, api_key: str) -> int:
    """Call the real place-search tool and display both response representations."""
    safe_print(f"Calling search_places with textQuery={query!r}...", api_key)
    result = await client.call_tool("search_places", {"textQuery": query})
    print(f"Tool status: {'error' if result.is_error else 'success'}")
    safe_print(f"\nText response:\n{result.text or '(empty)'}", api_key)
    structured = json.dumps(result.structured, ensure_ascii=False, indent=2)
    safe_print(f"\nStructured response:\n{structured}", api_key)
    return 1 if result.is_error else 0


async def main() -> int:
    """Parse CLI input, validate configuration, and run one MCP probe operation."""
    parser = argparse.ArgumentParser(
        description="List Google Maps Grounding Lite tools or search for places."
    )
    parser.add_argument(
        "query",
        nargs="?",
        help="Place search text, quoted as one argument. Omit to list tool schemas.",
    )
    args = parser.parse_args()
    if args.query is not None and not args.query.strip():
        parser.error("query must not be empty")

    try:
        settings = get_settings()
    except ValidationError as exc:
        fields = ", ".join(".".join(map(str, error["loc"])) for error in exc.errors())
        print(f"Invalid configuration. Check these fields: {fields}")
        return 1

    api_key = (settings.google_maps_api_key or "").strip()
    if not api_key:
        print("Missing GOOGLE_MAPS_API_KEY. Set it in backend/.env and run again.")
        return 1
    url = settings.google_maps_mcp_url.strip()
    if not url:
        print("Missing GOOGLE_MAPS_MCP_URL. Check backend/.env.")
        return 1

    print("Connecting to Google Maps Grounding Lite MCP...")
    try:
        async with McpClient(url, headers={"X-Goog-Api-Key": api_key}) as client:
            if args.query is not None:
                return await search_places(client, args.query.strip(), api_key)
            return await list_tools(client, api_key)
    except (McpError, httpx.HTTPError) as exc:
        safe_print(f"Probe failed ({type(exc).__name__}): {exc}", api_key)
        print("Check your network, project API enablement, and API key restrictions.")
        return 1


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
