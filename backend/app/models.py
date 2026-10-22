import uuid
from datetime import datetime

from sqlalchemy import CheckConstraint, DateTime, ForeignKey, Index, MetaData, String, Text, text
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

from app.config import get_settings

# The schema name is fixed at import time from settings (default "odyssey").
metadata = MetaData(schema=get_settings().db_schema)


class Base(DeclarativeBase):
    metadata = metadata
    type_annotation_map = {datetime: DateTime(timezone=True)}


_UUID_PK = dict(primary_key=True, default=uuid.uuid4, server_default=text("gen_random_uuid()"))
_NOW = dict(server_default=text("now()"))


class Trip(Base):
    """One generated itinerary per trip; owned by an anonymous session."""

    __tablename__ = "trips"
    __table_args__ = (
        CheckConstraint("status IN ('PENDING','READY','FAILED')", name="trips_status_check"),
        Index("ix_trips_session_created", "session_id", "created_at"),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), **_UUID_PK)
    session_id: Mapped[str] = mapped_column(String(64))
    title: Mapped[str | None] = mapped_column(Text)
    request: Mapped[dict] = mapped_column(JSONB)  # the five-step input as submitted
    itinerary: Mapped[dict | None] = mapped_column(JSONB)  # itinerary protocol v1 document
    status: Mapped[str] = mapped_column(String(16), default="PENDING", server_default="PENDING")
    created_at: Mapped[datetime] = mapped_column(**_NOW)
    updated_at: Mapped[datetime] = mapped_column(onupdate=text("now()"), **_NOW)


class Run(Base):
    """One execution of the agent workflow for a trip."""

    __tablename__ = "runs"
    __table_args__ = (
        CheckConstraint(
            "status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED')", name="runs_status_check"
        ),
        Index("ix_runs_trip_id", "trip_id"),
    )

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), **_UUID_PK)
    trip_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey(f"{metadata.schema}.trips.id", ondelete="CASCADE")
    )
    status: Mapped[str] = mapped_column(String(16), default="QUEUED", server_default="QUEUED")
    error: Mapped[dict | None] = mapped_column(JSONB)  # protocol error object on failure
    created_at: Mapped[datetime] = mapped_column(**_NOW)
    started_at: Mapped[datetime | None]
    finished_at: Mapped[datetime | None]
