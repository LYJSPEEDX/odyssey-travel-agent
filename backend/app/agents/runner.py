"""Shared run flow for the specialist agents (Transport, Stay, ...).

Each specialist supplies its tools, prompt and a `parse_final` that validates the model's
answer against the evidence; this module runs the ReAct loop around them, classifies the
"nothing to show" outcomes, gives the model one chance to repair a bad answer and returns
the trace.
"""

import json
import re
from collections.abc import Callable
from dataclasses import dataclass
from typing import Any, Generic, TypeVar

from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.messages import HumanMessage, SystemMessage

from app.agents.errors import AgentOutputError
from app.agents.react import TOOL_DATA_OPEN, AgentTool, EventSink, build_react_graph
from app.tools.models import ToolError

P = TypeVar("P")


def parse_json_object(text: str) -> dict:
    """The JSON object in a model reply (it may add prose or code fences around it)."""
    match = re.search(r"\{.*\}", text, re.DOTALL)
    if not match:
        raise ValueError("no JSON object found")
    try:
        data = json.loads(match.group(0))
    except json.JSONDecodeError as exc:
        raise ValueError(f"invalid JSON ({exc.msg})") from exc
    if not isinstance(data, dict):
        raise ValueError("the answer must be a JSON object")
    return data


@dataclass
class AgentRun(Generic[P]):
    evidence: dict[str, Any]
    parsed: P
    trace: list[dict]
    searches: int  # successful searches only; skipped or invalid calls do not count


async def run_agent(
    llm: BaseChatModel,
    tools: list[AgentTool],
    *,
    agent_name: str,
    system_prompt: str,
    request: str,
    max_searches: int,
    parse_final: Callable[[str, dict], P],
    no_data_marker: str,
    no_data_error: Callable[[], Exception],
    final_event: Callable[[P], dict] | None = None,
    on_event: EventSink | None = None,
) -> AgentRun[P]:
    """Run one specialist.

    parse_final(text, evidence) returns the validated answer or raises ValueError.
    no_data_marker is the text a search observation contains when the source had nothing;
    no_data_error builds the exception raised when that is all the agent found.
    """
    trace: list[dict] = []

    def sink(event: dict) -> None:
        trace.append(event)
        if on_event:
            on_event(event)

    graph = build_react_graph(llm, tools, max_tool_turns=max_searches, on_event=sink)
    final = await graph.ainvoke(
        {
            "messages": [SystemMessage(system_prompt), HumanMessage(request)],
            "turns": 0,
            "evidence": {},
        }
    )
    evidence: dict[str, Any] = final["evidence"]
    observations = [e["observation"] for e in trace if e["type"] == "tool"]
    if not evidence:
        if not observations:
            raise AgentOutputError(f"The {agent_name} agent answered without searching.")
        if any(no_data_marker in o for o in observations):
            raise no_data_error()
        raise ToolError(f"{agent_name} search failed: {observations[-1][:300]}")

    try:
        parsed = parse_final(str(final["messages"][-1].content), evidence)
    except ValueError as first_problem:
        # One repair attempt, then give up loudly.
        repair = await llm.ainvoke(
            [
                *final["messages"],
                HumanMessage(
                    f"That answer was not usable ({first_problem}). Reply again with ONLY the "
                    f"JSON object, using labels from: {', '.join(sorted(evidence))}."
                ),
            ]
        )
        sink({"type": "repair", "reason": str(first_problem), "content": str(repair.content)})
        try:
            parsed = parse_final(str(repair.content), evidence)
        except ValueError as exc:
            raise AgentOutputError(f"{agent_name} agent gave no usable answer: {exc}") from exc

    sink({"type": "final", **(final_event(parsed) if final_event else {})})
    searches = sum(1 for o in observations if o.startswith(TOOL_DATA_OPEN))
    return AgentRun(evidence=evidence, parsed=parsed, trace=trace, searches=searches)
