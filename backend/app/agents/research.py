"""Synchronous research API for exercising the specialist orchestration workflow."""

import asyncio
import json
import logging
import uuid
from typing import Annotated

import httpx
from fastapi import APIRouter, HTTPException, Query, Response
from pydantic import Field, ValidationError

from app.agents.destination import DestinationResult, NoPlacesError
from app.agents.errors import AgentOutputError
from app.agents.llm import LlmConfigError, build_chat_model
from app.agents.orchestrator import PlanningError, ResearchResult, run_research
from app.agents.react import EventSink
from app.agents.stay import NoStaysError, StayResult
from app.agents.transport import NoFlightsError, TransportResult
from app.config import get_settings
from app.schemas import TripRequest
from app.task_mapping import TaskConversionError, build_agent_tasks
from app.tools.mcp import McpError
from app.tools.models import ToolError

router = APIRouter(prefix="/research", tags=["research"])
logger = logging.getLogger("uvicorn.error.research")


class TransportResearchResponse(TransportResult):
    """Serialize flight research without internal event traces."""

    trace: list[dict] = Field(default_factory=list, exclude=True)


class StayResearchResponse(StayResult):
    """Serialize accommodation research without internal event traces."""

    trace: list[dict] = Field(default_factory=list, exclude=True)


class DestinationResearchResponse(DestinationResult):
    """Serialize place research without internal event traces."""

    trace: list[dict] = Field(default_factory=list, exclude=True)


class ResearchResponse(ResearchResult):
    """Expose research results while keeping traces in server logs."""

    transport: TransportResearchResponse
    stay: StayResearchResponse
    destination: DestinationResearchResponse


def redact(text: str, secrets: tuple[str, ...]) -> str:
    """Remove configured credentials from diagnostic output."""
    for secret in secrets:
        if secret:
            text = text.replace(secret, "[REDACTED]")
    return text


def event_logger(request_id: str, secrets: tuple[str, ...]) -> EventSink:
    """Create a per-request logger for specialist decisions and tool observations."""

    def emit(event: dict) -> None:
        """Write one event with its research request and specialist identifiers."""
        agent = event.get("agent", "unknown")
        kind = event["type"]
        prefix = f"[research={request_id}][agent={agent}]"
        if kind == "model":
            content = event.get("content", "")
            if event.get("turn", 0) > 0 and content.strip():
                message = f">>>>>>>>{prefix} model_content (turn {event['turn']})\n{content}"
                logger.info("%s", redact(message, secrets))
            for call in event.get("tool_calls", []):
                arguments = json.dumps(call["args"], ensure_ascii=False)
                message = f"{prefix} tool_call {call['name']}({arguments})"
                logger.info("%s", redact(message, secrets))
        elif kind == "tool":
            message = f"{prefix} tool_result {event['name']}\n{event['observation']}"
            logger.info("%s", redact(message, secrets))
        elif kind == "repair":
            logger.warning("%s repair %s", prefix, redact(str(event["reason"]), secrets))
        elif kind == "final":
            logger.info("%s completed", prefix)

    return emit


def failure(status: int, code: str, message: str, request_id: str) -> HTTPException:
    """Create a consistent error response with a log correlation identifier."""
    return HTTPException(
        status_code=status,
        detail={"code": code, "message": message, "request_id": request_id},
        headers={"X-Research-Id": request_id},
    )


@router.post("", response_model=ResearchResponse)
async def research(
    payload: TripRequest,
    response: Response,
    max_tool_turns: Annotated[int, Query(ge=1, le=8)] = 4,
) -> ResearchResponse:
    """Run real specialist research and return candidates without persisting a trip."""
    request_id = uuid.uuid4().hex
    response.headers["X-Research-Id"] = request_id
    logger.info("[research=%s] started", request_id)

    # Validate generation readiness before creating models or making external calls.
    try:
        build_agent_tasks(payload)
    except TaskConversionError as exc:
        logger.warning("[research=%s] invalid_request %s", request_id, exc)
        raise failure(422, "INVALID_REQUEST", str(exc), request_id) from exc

    try:
        settings = get_settings()
    except ValidationError as exc:
        fields = [".".join(map(str, item["loc"])) for item in exc.errors()]
        logger.error("[research=%s] invalid_configuration fields=%s", request_id, fields)
        raise failure(
            503, "CONFIGURATION_ERROR", "Server configuration is invalid.", request_id
        ) from exc

    secrets = tuple(
        value.strip()
        for value in (settings.google_maps_api_key or "", settings.llm_api_key or "")
        if value.strip()
    )
    try:
        missing = [
            name
            for name in (
                "kiwi_mcp_url",
                "trivago_mcp_url",
                "google_maps_mcp_url",
                "google_maps_api_key",
            )
            if not (getattr(settings, name) or "").strip()
        ]
        if missing:
            raise LlmConfigError("Missing configuration fields: " + ", ".join(missing))
        llm = build_chat_model(settings)
    except (LlmConfigError, ValueError) as exc:
        logger.error("[research=%s] configuration_error %s", request_id, redact(str(exc), secrets))
        raise failure(
            503, "CONFIGURATION_ERROR", "Check the server model and MCP configuration.", request_id
        ) from exc

    try:
        async with asyncio.timeout(settings.research_timeout_s):
            result = await run_research(
                llm,
                payload,
                settings,
                max_tool_turns=max_tool_turns,
                on_event=event_logger(request_id, secrets),
            )
    except TimeoutError as exc:
        logger.error("[research=%s] timed_out after=%ss", request_id, settings.research_timeout_s)
        raise failure(
            504, "RESEARCH_TIMEOUT", "Research exceeded the server time limit.", request_id
        ) from exc
    except (PlanningError, NoFlightsError, NoStaysError, NoPlacesError) as exc:
        logger.warning("[research=%s] no_plan %s", request_id, redact(str(exc), secrets))
        raise failure(
            409, "NO_USABLE_PLAN", "No usable plan was found. Check server logs.", request_id
        ) from exc
    except (AgentOutputError, ToolError, McpError, httpx.HTTPError) as exc:
        logger.error(
            "[research=%s] upstream_error %s: %s",
            request_id,
            type(exc).__name__,
            redact(str(exc), secrets),
        )
        raise failure(
            502, "RESEARCH_FAILED", "A model or data source failed. Check server logs.", request_id
        ) from exc
    except asyncio.CancelledError:
        logger.info("[research=%s] cancelled", request_id)
        raise
    except Exception as exc:
        logger.error(
            "[research=%s] unexpected_error %s: %s",
            request_id,
            type(exc).__name__,
            redact(str(exc), secrets),
        )
        raise failure(
            500, "INTERNAL_ERROR", "Research failed unexpectedly. Check server logs.", request_id
        ) from exc

    logger.info(
        "[research=%s] succeeded flights=%d stays=%d places=%d check_in=%s check_out=%s",
        request_id,
        len(result.transport.candidates),
        len(result.stay.candidates),
        len(result.destination.candidates),
        result.check_in,
        result.check_out,
    )
    return ResearchResponse.model_validate(result, from_attributes=True)
