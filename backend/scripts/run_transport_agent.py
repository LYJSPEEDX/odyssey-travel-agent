"""Run the Transport agent end to end against the live Kiwi MCP server.

    # with your configured model (LLM_* in .env)
    uv run python -m scripts.run_transport_agent --from Sydney --to Osaka \
        --depart 2026-12-10 --return 2026-12-14 --adults 2 \
        --constraint "avoid long flights" --constraint "no overnight layovers"

    # no model needed: canned replies, but real flight data
    uv run python -m scripts.run_transport_agent --scripted --from Sydney --to Osaka \
        --depart 2026-12-10 --return 2026-12-14
"""

import argparse
import asyncio
import json
import sys
from datetime import date

from app.agents.errors import AgentOutputError
from app.agents.fake import ScriptedChatModel
from app.agents.llm import LlmConfigError, build_chat_model
from app.agents.transport import (
    NoFlightsError,
    TransportTask,
    format_offer,
    run_transport_agent,
    scripted_reply_for,
)
from app.config import get_settings
from app.tools.mcp import McpError
from app.tools.models import ToolError


def print_event(event: dict) -> None:
    """Print transport model decisions, tool observations, and repair events."""
    kind = event["type"]
    if kind == "model":
        if event["content"].strip():
            print(f"\n[model] {event['content'].strip()[:500]}")
        for call in event["tool_calls"]:
            print(f"\n[model -> tool] {call['name']}({json.dumps(call['args'])})")
    elif kind == "tool":
        print("[tool result]\n" + event["observation"])
    elif kind == "repair":
        print(f"\n[repair] first answer unusable: {event['reason']}")


async def main() -> int:
    """Parse CLI input and invoke the self-contained transport agent."""
    p = argparse.ArgumentParser()
    p.add_argument("--from", dest="origin", required=True)
    p.add_argument("--to", dest="destination", required=True)
    p.add_argument("--depart", type=date.fromisoformat, required=True)
    p.add_argument("--return", dest="ret", type=date.fromisoformat)
    p.add_argument("--adults", type=int, default=1)
    p.add_argument("--children", type=int, default=0)
    p.add_argument("--currency", default="AUD")
    p.add_argument("--budget")
    p.add_argument("--constraint", action="append", default=[])
    p.add_argument("--scripted", action="store_true", help="use canned model replies")
    a = p.parse_args()

    task = TransportTask(
        origin=a.origin, destination=a.destination, depart_date=a.depart, return_date=a.ret,
        adults=a.adults, children=a.children, currency=a.currency, budget=a.budget,
        constraints=a.constraint,
    )  # fmt: skip
    settings = get_settings()
    try:
        llm = (
            ScriptedChatModel(script=scripted_reply_for(task))
            if a.scripted
            else build_chat_model(settings)
        )
    except LlmConfigError as exc:
        print(f"FAIL config: {exc}")
        return 1

    try:
        result = await run_transport_agent(llm, task, settings=settings, on_event=print_event)
    except (NoFlightsError, AgentOutputError, McpError, ToolError) as exc:
        print(f"\nFAIL {type(exc).__name__}: {exc}")
        return 1

    print(f"\n=== RESULT ({result.searches} search(es)) ===")
    for i, offer in enumerate(result.candidates, 1):
        print(f"{i}. {format_offer(f'#{i}', offer)}")
        print(f"   book: {offer.booking_url}")
    print("Rationale:", result.rationale)
    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
