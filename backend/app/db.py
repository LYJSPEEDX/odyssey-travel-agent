from sqlalchemy import text
from sqlalchemy.ext.asyncio import (
    AsyncEngine,
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)

from app.config import Settings
from app.models import Base


def create_engine(settings: Settings) -> AsyncEngine:
    return create_async_engine(settings.database_url, pool_pre_ping=True)


def create_session_factory(engine: AsyncEngine) -> async_sessionmaker[AsyncSession]:
    return async_sessionmaker(engine, expire_on_commit=False)


async def init_db(engine: AsyncEngine, schema: str) -> None:
    """Idempotent: create the schema and any missing tables. No migration framework."""
    async with engine.begin() as conn:
        await conn.execute(text(f'CREATE SCHEMA IF NOT EXISTS "{schema}"'))
        await conn.run_sync(Base.metadata.create_all)


async def ping(engine: AsyncEngine) -> None:
    async with engine.connect() as conn:
        await conn.execute(text("SELECT 1"))
