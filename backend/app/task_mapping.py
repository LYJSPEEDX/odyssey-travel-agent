"""Convert TripRequest into initial specialist tasks without model inference."""

from pydantic import BaseModel

from app.agents.destination import DestinationTask
from app.agents.stay import StayTask
from app.agents.transport import TransportTask
from app.schemas import TripRequest


class TaskConversionError(ValueError):
    """The request is valid for storage but cannot yet produce supported agent tasks."""


class AgentTasks(BaseModel):
    """Group the initial tasks produced from one authoritative request."""

    transport: TransportTask
    stay: StayTask
    destination: DestinationTask


def build_agent_tasks(request: TripRequest) -> AgentTasks:
    """Build adult-only research tasks and treat submitted notes as preferences.

    The MVP interprets endDate as departure from the destination. Stay and
    destination dates are initial research windows; orchestration must align them
    with the selected flight's actual arrival before scheduling. The accommodation
    market keeps StayTask's existing default. Budget text is preserved verbatim.
    This function performs no network calls, model inference, or budget filtering.
    """
    missing = [name for name in ("adults", "currency") if getattr(request, name) is None]
    if missing:
        raise TaskConversionError("Generation requires explicit fields: " + ", ".join(missing))
    if request.endDate <= request.startDate:
        raise TaskConversionError("The MVP requires at least one overnight stay.")

    preferences = [request.notes[step] for step in sorted(request.notes)]
    companion_note = f"Companion description: {request.companion}"
    notes = [companion_note, *(f"Traveller preference: {item}" for item in preferences)]
    return AgentTasks(
        transport=TransportTask(
            origin=request.departure,
            destination=request.destination,
            depart_date=request.startDate,
            return_date=request.endDate,
            adults=request.adults,
            children=0,
            currency=request.currency,
            budget=request.budget,
            constraints=request.constraints,
            notes=notes,
        ),
        stay=StayTask(
            destination=request.destination,
            check_in=request.startDate,
            check_out=request.endDate,
            adults=request.adults,
            children_ages=[],
            currency=request.currency,
            budget=request.budget,
            constraints=request.constraints,
            notes=notes,
        ),
        destination=DestinationTask(
            destination=request.destination,
            start_date=request.startDate,
            end_date=request.endDate,
            adults=request.adults,
            children_ages=[],
            currency=request.currency,
            budget=request.budget,
            preferences=preferences,
            constraints=request.constraints,
            notes=[companion_note],
        ),
    )
