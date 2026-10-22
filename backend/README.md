# Odyssey Backend

FastAPI + PostgreSQL backend for the Odyssey travel agent (LangGraph agents to follow).
This is the foundation: configuration, health check, anonymous session id and the database layer.

## Local setup

### 1. Prerequisites

You only need **git** and **[uv](https://docs.astral.sh/uv/)**. Python is not required up front:
uv downloads the version declared in `pyproject.toml` (`>=3.11`) automatically.

```sh
curl -LsSf https://astral.sh/uv/install.sh | sh                  # macOS / Linux
powershell -c "irm https://astral.sh/uv/install.ps1 | iex"       # Windows
```

### 2. Get the code and install dependencies

```sh
git clone https://github.com/LYJSPEEDX/odyssey-travel-agent.git
cd odyssey-travel-agent
git checkout feat/backend-travel-agent
cd backend
uv sync
```

### 3. Configure `.env`

```sh
cp .env.example .env        # Windows: copy .env.example .env
```

Edit `backend/.env` and point it at your PostgreSQL instance:

```ini
DB_HOST=<host>
DB_PORT=<port>
DB_USER=<user>
DB_PASSWORD=<password>
DB_NAME=<database>
DB_SCHEMA=odyssey

# Leave empty for now; filled in when the agents are added
LLM_PROVIDER=
LLM_MODEL=
LLM_API_KEY=
LLM_BASE_URL=
```

- Write special characters in the password (such as `@`) as-is, no URL escaping needed.
- `DB_SCHEMA` must match the schema created by `sql/init.sql` (default `odyssey`).
- `.env` is git-ignored. Never commit credentials.

### 4. Verify the database, then start the server

```sh
uv run python -m scripts.db_check
```

You should see four `OK` lines and `All checks passed.` It writes one test trip and run and
deletes them again, so it leaves no data behind and is safe to run repeatedly.

```sh
uv run uvicorn app.main:app --reload
```

- Listens on `http://127.0.0.1:8000`; `--reload` restarts on code changes.
- Health check: `curl -i localhost:8000/healthz` returns `{"status":"ok","db":"ok"}` and an
  `X-Session-Id` response header.
- Interactive API docs: <http://localhost:8000/docs>.

### 5. Connecting from Android (optional)

- Android emulator: use `http://10.0.2.2:8000` to reach the host machine.
- Physical device on the same Wi-Fi: listen on all interfaces and use the computer's LAN IP.

```sh
uv run uvicorn app.main:app --reload --host 0.0.0.0
```

### Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `connection failed` or a timeout on startup | Wrong host or port, or your network/firewall cannot reach the database. Run `db_check` to see the exact error. |
| `password authentication failed` | Wrong user or password in `.env`. |
| `/healthz` returns 503 | The database connection dropped after startup; check the server log. |
| Server exits immediately with a DB error | By design: if the database is unreachable the app does not start, rather than running in a broken state. |
| Changes to `.env` have no effect | Restart uvicorn; settings are read once at startup. |

## Database

- Tables live in their own PostgreSQL schema (`DB_SCHEMA`, default `odyssey`): `trips`, `runs`.
- `sql/init.sql` is the same DDL for manual setup (`psql -f sql/init.sql`) and is idempotent.
  It is generated from `app/models.py`. After changing a model, regenerate it and commit the result:

  ```sh
  uv run python -m scripts.gen_init_sql
  ```

- The app also creates any missing schema/tables on startup (`CREATE ... IF NOT EXISTS`); there is
  no migration framework, so changes to existing columns must be applied to the database by hand.

## Session id

Every response carries an `X-Session-Id` header. Send it back on later requests to stay in the
same anonymous session; if it is absent or malformed a new one is minted. There is no login.
