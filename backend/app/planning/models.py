"""Component-owned tables in the shared SQLAlchemy metadata/schema."""

import uuid
from datetime import datetime

from sqlalchemy import ForeignKey, String, UniqueConstraint, text
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.models import Base, metadata


class PlanningSession(Base):
    __tablename__ = "planning_sessions"
    __table_args__ = (UniqueConstraint("owner", "creation_request_id"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    owner: Mapped[str] = mapped_column(String(64), index=True)
    creation_request_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True))
    profile: Mapped[dict] = mapped_column(JSONB)
    revision: Mapped[int] = mapped_column(default=0)
    state: Mapped[dict] = mapped_column(JSONB)
    created_at: Mapped[datetime] = mapped_column(server_default=text("now()"))
    updated_at: Mapped[datetime] = mapped_column(
        server_default=text("now()"), onupdate=text("now()")
    )


class PlanningTurn(Base):
    __tablename__ = "planning_turns"
    __table_args__ = (UniqueConstraint("session_id", "request_id"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    session_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey(f"{metadata.schema}.planning_sessions.id", ondelete="CASCADE"), index=True
    )
    request_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True))
    fingerprint: Mapped[str] = mapped_column(String(64))
    status: Mapped[str] = mapped_column(String(16), default="PROCESSING")
    response: Mapped[dict | None] = mapped_column(JSONB, nullable=True)
    http_status: Mapped[int | None] = mapped_column(nullable=True)
    created_at: Mapped[datetime] = mapped_column(server_default=text("now()"))
