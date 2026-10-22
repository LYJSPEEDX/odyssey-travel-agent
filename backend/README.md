# Odyssey Backend

FastAPI + PostgreSQL (LangGraph agents to follow). This is the foundation: config, health check,
anonymous session id, and the database layer.

## Run

```sh
cd backend
cp .env.example .env        # fill in DB_* (never commit .env)
uv sync
uv run python -m scripts.db_check          # verify the DB connection, create tables, round-trip a row
uv run uvicorn app.main:app --reload       # tables are also created on startup (idempotent)
curl -i localhost:8000/healthz             # {"status":"ok","db":"ok"} + X-Session-Id header
```

## Database

- Tables live in their own PG schema (`DB_SCHEMA`, default `odyssey`): `trips`, `runs`.
- `sql/init.sql` is the same DDL for manual setup (`psql -f sql/init.sql`); it is idempotent.
  It is generated from `app/models.py` — after changing models run
  `uv run python -m scripts.gen_init_sql` and commit the result.
- The password is a separate setting (`DB_PASSWORD`), so characters like `@` need no escaping.

## Session id

Every response carries `X-Session-Id`. Send it back on later requests to stay in the same
anonymous session; if absent or malformed, a new one is minted. No login.
