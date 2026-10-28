"""Run the Destination agent with a real model and Google Maps data."""

import argparse
import asyncio
import json
import sys
from datetime import date

import httpx

from app.agents.destination import (
    DestinationTask,
    NoPlacesError,
    run_destination_agent,
)
from app.agents.errors import AgentOutputError
from app.agents.llm import LlmConfigError, build_chat_model
from app.config import get_settings
from app.tools.mcp import McpError
from app.tools.models import ToolError


def print_event(event: dict) -> None:
    """Show tool calls and answer-repair events during the agent run."""
    kind = event["type"]

    if kind == "model":
        for call in event["tool_calls"]:
            arguments = json.dumps(call["args"], ensure_ascii=False)
            print(f"[model -> tool] {call['name']}({arguments})")
    elif kind == "tool":
        print(f"[tool completed] {event['name']}")
        print(event["observation"])
    elif kind == "repair":
        print(f"[repair] {event['reason']}")


async def main() -> int:
    """Read CLI arguments and run destination research end to end."""
    parser = argparse.ArgumentParser(
        description="Research destination candidates using real Google Maps data."
    )
    parser.add_argument("--to", dest="destination", required=True)
    parser.add_argument("--start", type=date.fromisoformat, required=True)
    parser.add_argument("--end", type=date.fromisoformat, required=True)
    parser.add_argument("--preference", action="append", default=[])
    parser.add_argument("--constraint", action="append", default=[])
    parser.add_argument("--max-tool-turns", type=int, default=4)
    args = parser.parse_args()

    if args.end < args.start:
        parser.error("--end must be on or after --start")
    if args.max_tool_turns < 1:
        parser.error("--max-tool-turns must be at least 1")

    task = DestinationTask(
        destination=args.destination,
        start_date=args.start,
        end_date=args.end,
        preferences=args.preference,
        constraints=args.constraint,
    )

    settings = get_settings()
    api_key = (settings.google_maps_api_key or "").strip()

    try:
        llm = build_chat_model(settings)
    except LlmConfigError as exc:
        print(f"Model configuration error: {exc}")
        return 1

    print("Starting destination research...")

    try:
        result = await run_destination_agent(
            llm,
            task,
            settings=settings,
            max_tool_turns=args.max_tool_turns,
            on_event=print_event,
        )
    except (
        NoPlacesError,
        AgentOutputError,
        McpError,
        ToolError,
        httpx.HTTPError,
    ) as exc:
        detail = str(exc)
        if api_key:
            detail = detail.replace(api_key, "[REDACTED]")
        print(f"Research failed ({type(exc).__name__}): {detail}")
        return 1

    print("\n=== RESULT ===")
    print(result.model_dump_json(indent=2, exclude={"trace"}))
    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
