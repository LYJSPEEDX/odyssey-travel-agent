"""Run the Stay agent end to end against the live trivago MCP server.

    # with your configured model (LLM_* in .env)
    uv run python -m scripts.run_stay_agent --to "Kyoto, Japan" --in 2026-12-10 --out 2026-12-14 \
        --adults 2 --child-age 7 --constraint "travelling with a dog" --constraint "good reviews"

    # no model needed: canned replies, but real hotel data
    uv run python -m scripts.run_stay_agent --scripted --to "Kyoto, Japan" \
        --in 2026-12-10 --out 2026-12-14
"""

import argparse
import asyncio
import json
import sys
from datetime import date

from app.agents.errors import AgentOutputError
from app.agents.fake import ScriptedChatModel
from app.agents.llm import LlmConfigError, build_chat_model
from app.agents.stay import (
    TRIVAGO_URL,
    NoStaysError,
    StayTask,
    format_stay,
    run_stay_agent,
    scripted_reply_for,
)
from app.config import get_settings
from app.tools.mcp import McpClient, McpError
from app.tools.models import ToolError


def print_event(event: dict) -> None:
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
    p = argparse.ArgumentParser()
    p.add_argument("--to", dest="destination", required=True)
    p.add_argument("--in", dest="check_in", type=date.fromisoformat, required=True)
    p.add_argument("--out", dest="check_out", type=date.fromisoformat, required=True)
    p.add_argument("--adults", type=int, default=2)
    p.add_argument("--child-age", type=int, action="append", default=[])
    p.add_argument("--country", default="AU")
    p.add_argument("--currency", default="AUD")
    p.add_argument("--budget")
    p.add_argument("--constraint", action="append", default=[])
    p.add_argument("--scripted", action="store_true", help="use canned model replies")
    a = p.parse_args()

    task = StayTask(
        destination=a.destination, check_in=a.check_in, check_out=a.check_out, adults=a.adults,
        children_ages=a.child_age, country=a.country, currency=a.currency, budget=a.budget,
        constraints=a.constraint,
    )  # fmt: skip
    try:
        llm = (
            ScriptedChatModel(script=scripted_reply_for(task))
            if a.scripted
            else build_chat_model(get_settings())
        )
    except LlmConfigError as exc:
        print(f"FAIL config: {exc}")
        return 1

    try:
        async with McpClient(TRIVAGO_URL) as client:
            result = await run_stay_agent(llm, client, task, on_event=print_event)
    except (NoStaysError, AgentOutputError, McpError, ToolError) as exc:
        print(f"\nFAIL {type(exc).__name__}: {exc}")
        return 1

    print(f"\n=== RESULT ({result.searches} search(es), {result.rooms} room(s)) ===")
    for i, c in enumerate(result.candidates, 1):
        star = " (recommended)" if c.offer.id == result.recommended_id else ""
        print(f"{i}.{star} {format_stay(f'#{i}', c.offer, c.rooms, task.nights)}")
        print(f"   book: {c.offer.booking_url}")
    print("Rationale:", result.rationale)
    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
