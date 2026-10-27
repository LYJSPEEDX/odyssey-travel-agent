"""Verify the database connection from the current machine.

    uv run python -m scripts.db_check

Connects with the settings in .env, creates the schema and tables if missing, then writes,
reads back and deletes one trip and one run. Safe to run repeatedly.
"""

import asyncio
import sys

from sqlalchemy import select, text

from app.config import get_settings
from app.db import create_engine, create_session_factory, init_db
from app.models import Run, Trip


async def main() -> int:
    s = get_settings()
    print(
        f"Connecting to {s.db_host}:{s.db_port}/{s.db_name} as {s.db_user} (schema {s.db_schema})"
    )
    engine = create_engine(s)
    try:
        async with engine.connect() as conn:
            version = (await conn.execute(text("SELECT version()"))).scalar_one()
        print("OK  connected:", version.split(",")[0])

        await init_db(engine, s.db_schema)
        print(f"OK  schema '{s.db_schema}' and tables ready")

        factory = create_session_factory(engine)
        async with factory() as session:
            trip = Trip(session_id="db-check-session", request={"check": True})
            session.add(trip)
            await session.flush()
            run = Run(trip_id=trip.id)
            session.add(run)
            await session.commit()

            got = (await session.execute(select(Run).where(Run.id == run.id))).scalar_one()
            assert got.status == "QUEUED" and got.trip_id == trip.id
            print("OK  wrote and read back a trip + run (defaults applied)")

            await session.delete(trip)  # cascades to the run
            await session.commit()
            left = (await session.execute(select(Run).where(Run.id == run.id))).first()
            assert left is None
            print("OK  cleaned up (cascade delete works)")
    except Exception as exc:
        print(f"FAIL {type(exc).__name__}: {exc}")
        return 1
    finally:
        await engine.dispose()
    print("All checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
