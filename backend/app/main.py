from contextlib import asynccontextmanager

from fastapi import FastAPI, Response

from app.config import get_settings
from app.db import create_engine, create_session_factory, init_db, ping
from app.recommendations import router as recommendations_router
from app.session import SessionIdMiddleware
from app.trips import router as trips_router


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    engine = create_engine(settings)
    await init_db(engine, settings.db_schema)  # fail fast if the database is unreachable
    app.state.engine = engine
    app.state.session_factory = create_session_factory(engine)
    yield
    await engine.dispose()


def create_app() -> FastAPI:
    app = FastAPI(title="Odyssey Backend", version="0.1.0", lifespan=lifespan)
    app.add_middleware(SessionIdMiddleware)
    app.include_router(trips_router)
    app.include_router(recommendations_router)

    @app.get("/healthz")
    async def healthz(response: Response) -> dict:
        try:
            await ping(app.state.engine)
        except Exception as exc:  # report, don't hide
            response.status_code = 503
            return {"status": "error", "db": f"{type(exc).__name__}"}
        return {"status": "ok", "db": "ok"}

    return app


app = create_app()
