"""Coordinate specialist research before itinerary scheduling and assembly."""

from datetime import date

from langchain_core.language_models.chat_models import BaseChatModel
from pydantic import BaseModel

from app.agents.destination import (
    DestinationResult,
    DestinationTask,
    run_destination_agent,
)
from app.agents.react import EventSink
from app.agents.stay import (
    StayResult,
    StayTask,
    run_stay_agent,
)
from app.agents.transport import TransportResult, run_transport_agent
from app.config import Settings
from app.schemas import TripRequest
from app.task_mapping import build_agent_tasks
from app.tools.models import FlightOffer


class PlanningError(ValueError):
    """Indicate that the research results cannot form a supported plan."""


class ResearchResult(BaseModel):
    """Collect specialist results and the flight used for initial planning."""

    request: TripRequest
    transport: TransportResult
    planning_flight_id: str
    check_in: date
    check_out: date
    stay: StayResult
    destination: DestinationResult


def scoped_events(
    agent: str,
    on_event: EventSink | None,
) -> EventSink:
    """Attach the specialist name before forwarding an event."""

    def emit(event: dict) -> None:
        """Forward one event without modifying the original event object."""
        if on_event is not None:
            on_event({**event, "agent": agent})

    return emit


def select_planning_flight(
    transport: TransportResult,
    request: TripRequest,
) -> FlightOffer:
    """Select the first candidate matching fixed dates and an overnight stay."""
    for offer in transport.candidates:
        if offer.currency != request.currency:
            continue

        if offer.outbound.departure.date() != request.startDate:
            continue

        if offer.inbound is None:
            continue

        if offer.inbound.departure.date() != request.endDate:
            continue

        if offer.outbound.arrival.date() >= offer.inbound.departure.date():
            continue

        return offer

    raise PlanningError(
        "No flight candidate matches the requested dates, currency, and an overnight stay."
    )


async def run_research(
    llm: BaseChatModel,
    request: TripRequest,
    settings: Settings,
    *,
    max_tool_turns: int = 4,
    on_event: EventSink | None = None,
) -> ResearchResult:
    """Run specialist research using one shared request and aligned dates."""
    tasks = build_agent_tasks(request)

    if max_tool_turns < 1:
        raise PlanningError("max_tool_turns must be at least 1.")

    api_key = (settings.google_maps_api_key or "").strip()
    if not api_key:
        raise PlanningError("GOOGLE_MAPS_API_KEY is required.")

    if not settings.google_maps_mcp_url.strip():
        raise PlanningError("GOOGLE_MAPS_MCP_URL is required.")

    transport = await run_transport_agent(
        llm,
        tasks.transport,
        settings=settings,
        max_searches=max_tool_turns,
        on_event=scoped_events("transport", on_event),
    )

    flight = select_planning_flight(transport, request)
    check_in = flight.outbound.arrival.date()
    check_out = request.endDate

    stay_task = StayTask.model_validate(
        {
            **tasks.stay.model_dump(),
            "check_in": check_in,
            "check_out": check_out,
        }
    )

    destination_task = DestinationTask.model_validate(
        {
            **tasks.destination.model_dump(),
            "start_date": check_in,
            "end_date": check_out,
        }
    )

    stay = await run_stay_agent(
        llm,
        stay_task,
        settings=settings,
        max_searches=max_tool_turns,
        on_event=scoped_events("stay", on_event),
    )

    destination = await run_destination_agent(
        llm,
        destination_task,
        settings=settings,
        max_tool_turns=max_tool_turns,
        on_event=scoped_events("destination", on_event),
    )

    return ResearchResult(
        request=request,
        transport=transport,
        planning_flight_id=flight.id,
        check_in=check_in,
        check_out=check_out,
        stay=stay,
        destination=destination,
    )
