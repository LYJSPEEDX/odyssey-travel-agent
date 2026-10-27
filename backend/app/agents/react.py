"""A small, bounded ReAct loop on LangGraph, reusable by every research agent.

    reason (LLM, tools bound) --tool calls--> act (run tools) --> reason ... --> END

The loop is bounded: after `max_tool_turns` rounds of tool use the model gets one last turn
with no tools and must answer. Tool failures become observations the model can react to;
anything else (programming errors) propagates.
"""

from collections.abc import Awaitable, Callable
from dataclasses import dataclass, field
from typing import Annotated, Any, TypedDict

from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.messages import AnyMessage, HumanMessage, ToolMessage
from langgraph.graph import END, START, StateGraph
from langgraph.graph.message import add_messages
from pydantic import BaseModel, ValidationError

from app.tools.mcp import McpError
from app.tools.models import ToolError

MAX_OBSERVATION_CHARS = 6000
TOOL_DATA_OPEN = "<tool_data untrusted>"
EventSink = Callable[[dict], None]


def untrusted(text: str) -> str:
    """Wrap external data so the model treats it as content, never as instructions."""
    return f"{TOOL_DATA_OPEN}\n{text}\n</tool_data>"


@dataclass
class ToolOutput:
    text: str  # what the model sees
    evidence: dict[str, Any] = field(default_factory=dict)  # full objects kept out of the prompt


@dataclass
class AgentTool:
    name: str
    description: str
    args_schema: type[BaseModel]
    run: Callable[[Any, dict], Awaitable[ToolOutput]]  # (validated args, evidence so far)

    def spec(self) -> dict:
        return {
            "type": "function",
            "function": {
                "name": self.name,
                "description": self.description,
                "parameters": self.args_schema.model_json_schema(),
            },
        }


def _merge(old: dict, new: dict) -> dict:
    return {**old, **new}


class ReactState(TypedDict):
    messages: Annotated[list[AnyMessage], add_messages]
    turns: int  # completed rounds of tool use
    evidence: Annotated[dict, _merge]


def build_react_graph(
    llm: BaseChatModel,
    tools: list[AgentTool],
    *,
    max_tool_turns: int = 4,
    max_calls_per_turn: int = 2,
    on_event: EventSink | None = None,
):
    by_name = {t.name: t for t in tools}
    tool_model = llm.bind_tools([t.spec() for t in tools])

    def emit(**event: Any) -> None:
        if on_event:
            on_event(event)

    async def reason(state: ReactState) -> dict:
        final_turn = state["turns"] >= max_tool_turns
        messages = state["messages"]
        if final_turn:
            messages = messages + [
                HumanMessage("Tool budget used up. Give your final answer now, without tools.")
            ]
        ai = await (llm if final_turn else tool_model).ainvoke(messages)
        calls = [] if final_turn else list(ai.tool_calls)
        emit(type="model", turn=state["turns"], content=str(ai.content), tool_calls=calls)
        return {"messages": [ai], "turns": state["turns"] + (1 if calls else 0)}

    def route(state: ReactState) -> str:
        last = state["messages"][-1]
        wants_tools = bool(getattr(last, "tool_calls", None))
        return "act" if wants_tools and state["turns"] <= max_tool_turns else END

    async def act(state: ReactState) -> dict:
        evidence = dict(state["evidence"])
        replies: list[ToolMessage] = []
        for index, call in enumerate(state["messages"][-1].tool_calls):
            if index >= max_calls_per_turn:  # every call id must still get a reply
                text = (
                    f"ERROR: at most {max_calls_per_turn} tool calls per turn; this one was skipped"
                )
            else:
                text = await _run_call(call, by_name, evidence)
            replies.append(ToolMessage(content=text, tool_call_id=call["id"], name=call["name"]))
            emit(type="tool", name=call["name"], args=call["args"], observation=text)
        return {"messages": replies, "evidence": evidence}

    async def _run_call(call: dict, tools_by_name: dict[str, AgentTool], evidence: dict) -> str:
        tool = tools_by_name.get(call["name"])
        if tool is None:
            return f"ERROR: unknown tool {call['name']!r}. Available: {', '.join(tools_by_name)}"
        try:
            args = tool.args_schema(**call["args"])
        except ValidationError as exc:
            problems = "; ".join(
                f"{'.'.join(map(str, e['loc']))}: {e['msg']}" for e in exc.errors()
            )
            return f"ERROR: invalid arguments ({problems})"
        try:
            output = await tool.run(args, evidence)
        except (TimeoutError, ToolError, McpError) as exc:
            return f"ERROR: {type(exc).__name__}: {exc}"
        evidence.update(output.evidence)
        return output.text[:MAX_OBSERVATION_CHARS]

    graph = StateGraph(ReactState)
    graph.add_node("reason", reason)
    graph.add_node("act", act)
    graph.add_edge(START, "reason")
    graph.add_conditional_edges("reason", route, {"act": "act", END: END})
    graph.add_edge("act", "reason")
    return graph.compile()
