from fastapi import FastAPI

from app.session import SessionIdMiddleware


def create_app() -> FastAPI:
    app = FastAPI(title="Odyssey Backend", version="0.1.0")
    app.add_middleware(SessionIdMiddleware)

    @app.get("/healthz")
    async def healthz() -> dict:
        return {"status": "ok"}

    return app


app = create_app()
