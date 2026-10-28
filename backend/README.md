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

## Trip API

The API maps to the latest Android sources in `root/odyssey-travel-agent/Frontend/`,
particularly `TripPlanState` and `SavedTrip.fromContract`. It does not yet replace
Android's local mock flow; the frontend network client is still to be implemented.

| Method | Path | Result |
|---|---|---|
| POST | `/trips` | 201: persist a validated request and QUEUED run atomically |
| GET | `/trips?limit=20&offset=0` | 200: this session's summaries, newest first |
| GET | `/trips/{trip_id}` | 200: request, status, itinerary (nullable), latest run |
| GET | `/trips/{trip_id}/runs/{run_id}` | 200: execution status and error |
| GET | `/trips/{trip_id}/itinerary` | 200: bare v1 itinerary; 409 while unavailable |
| DELETE | `/trips/{trip_id}` | 204: delete trip and its runs |

Missing trips, trips belonging to another session, and missing runs return 404.
Invalid request fields, UUIDs or pagination parameters return 422. List responses
are `{ "items": [...], "limit": 20, "offset": 0 }` and omit full itinerary/request JSON.
Detail responses use database status values PENDING/READY/FAILED and include
`latest_run` (QUEUED/RUNNING/SUCCEEDED/FAILED). Response metadata uses snake_case
(`created_at`, `updated_at`, `latest_run`); request fields use Android-style camelCase.
The POST response also includes `Location: /trips/{id}`.

**Generation boundary:** no worker or LLM is implemented. POST stores a PENDING trip
and QUEUED run; it does not schedule background execution or produce an itinerary.
Do not poll indefinitely expecting completion in this version. Future generation
code must validate the complete shared v1 schema and semantics before saving an
itinerary and setting READY; no public endpoint accepts arbitrary READY/status updates.

### Request example

```json
{
  "departure": "Sydney",
  "destination": "Kyoto",
  "startDate": "2026-11-01",
  "endDate": "2026-11-03",
  "companion": "Solo",
  "budget": "AUD 200 per person per day",
  "constraints": ["Less walking"],
  "notes": {"5": "Museums and food"}
}
```

`departure`, `destination`, `companion`, and `budget` map directly to the corresponding
`TripPlanState` getters. Convert Android's YYYYMMDD date integers to ISO YYYY-MM-DD
strings; do not send integers or zero dates. Both dates are required and non-null.
Date ranges include both endpoints and must contain 1–60 days.
Companion and budget accept custom text as the form does; budget is not assumed to
be a numeric whole-trip amount. Constraints map to `getConstraints()`; notes use
keys `"1"` through `"5"` for nonempty submitted `getNote(step)` values. Do not send
unsent drafts or UI generation state. Unknown top-level fields are rejected.

### Android integration

1. Use `http://10.0.2.2:8000` on the emulator or the computer's LAN IP on a phone.
2. Send POST `/trips` on a background thread. Persist the response's `X-Session-Id`
   and send that header on subsequent requests. Keep the trip ID and latest run ID.
3. Once a generation worker exists, read `/trips/{id}` to inspect progress. Stop on
   FAILED, handle error information in `latest_run.error`, and enforce a timeout.
4. On READY, fetch `/trips/{id}/itinerary`. This returns the bare document expected
   by `SavedTrip.fromContract(context, responseJson)`, not the trip metadata envelope.
5. Save the snapshot with `TripStore.save` off the UI thread; only open
   `TripDetailActivity` after successful persistence. Backend storage and local
   storage are separate; deleting a server trip does not erase a local snapshot.

Android still needs INTERNET permission, development HTTP configuration, an API
client, lifecycle-aware request handling, and errors/retries. Native Android HTTP
does not require browser CORS configuration. Anonymous session IDs are bearer
identifiers, not authenticated accounts: retain them privately and use HTTPS when
hosting the API. POST is not idempotent; automatically retrying a successful but
interrupted request can create another trip.

### Tests

```sh
uv run python -m unittest discover -s tests -v
RUN_DB_TESTS=1 uv run python -m unittest discover -s tests -v
```

The first command runs validation/HTTP validation tests without connecting to PostgreSQL.
The second also exercises create/list/read/run lookup/itinerary/delete and session isolation
against the configured database. Existing tables must already be initialized. Database tests
use savepoints inside an outer transaction and roll back all test records. They do not
initialize or migrate the schema. No additional test dependencies are required.

## Profile-based location recommendations

`POST /recommendations/locations` accepts the latest frontend's profile recommendation
request and returns a departure plus destination suggestions with reasons. It uses a
small backend demo catalog and preference keyword ranking; no LLM or live travel service
is called. Profiles are used transiently, not stored. Locations require user confirmation
before the five-step form is submitted to POST `/trips`.

See [profile-to-planning workflow](docs/profile-planning-workflow.md) for request/response
examples, frontend handoff, validation, provider limitations, and verification steps.

## Agents (work in progress)

Configure the model in `.env` (see `.env.example`): `LLM_PROVIDER` (`openai` for any
OpenAI-compatible server, `anthropic` with `uv sync --extra anthropic`), `LLM_MODEL`,
`LLM_BASE_URL` (e.g. `http://localhost:11434/v1` for Ollama), `LLM_API_KEY` (optional for local
servers).

```sh
uv run python -m scripts.llm_check                      # one message to your model
uv run python -m scripts.kiwi_check --from Sydney --to Osaka --depart 2026-12-10 --return 2026-12-14
uv run python -m scripts.run_transport_agent --from Sydney --to Osaka \
    --depart 2026-12-10 --return 2026-12-14 --adults 2 --constraint "avoid long flights"
uv run python -m scripts.run_transport_agent --scripted ...   # canned model, real flight data
uv run python -m scripts.run_stay_agent --to "Kyoto, Japan" --in 2026-12-10 --out 2026-12-14 \
    --adults 2 --child-age 7 --constraint "travelling with a dog" --constraint "good reviews"
uv run python -m scripts.run_stay_agent --scripted ...        # canned model, real hotel data
```

Data-source adapters (all live, none need an API key) each have a check script:

```sh
uv run python -m scripts.kiwi_check --from Sydney --to Osaka --depart 2026-12-10   # flights
uv run python -m scripts.trivago_check --to "Kyoto, Japan" --in 2026-12-10 --out 2026-12-13 \
    --min-stars 4 --feature pet_friendly                                           # hotels
uv run python -m scripts.fx_check --from AUD --to JPY --amount 1500                # ECB rates
uv run python -m scripts.weather_check --place "Kyoto, Japan" --from 2026-12-10 --to 2026-12-13
```

Notes: flight and hotel prices are quotes, not bookings; hotel totals cover all rooms for the
whole stay. Weather is a real forecast for the next ~2 weeks and labelled `typical` (average of
the same dates in the previous 3 years) beyond that. Open-Meteo's free tier is limited per IP per
day; a 429 is surfaced as an error.

The Stay agent works the same way on trivago data: the model picks the query, the number of rooms
and filters (stars, guest score, features such as `pet_friendly`); it returns 3-5 distinct
properties and one recommendation. Dates, party, currency and market are fixed by the trip facts.
Shared run logic (bounded loop, empty-result classification, one repair attempt) is in
`app/agents/runner.py`.

The Transport agent is a bounded ReAct loop (`app/agents/react.py`): the model calls
`search_flights` (real Kiwi MCP data), sees a compact, labelled shortlist, and answers with the
chosen labels. Party size and currency come from the trip facts, not from the model.


## Explicit planning facts and task conversion

Request schema: [docs/schema](../docs/schema/README.md).

`TripRequest` remains the public planning input. Only two fields are added:

- `adults`: explicit integer adult count, 1-20; required before task conversion.
- `currency`: three-letter search/display currency; required before task conversion.
  Codes are trimmed and uppercased; upstream currency support is not inferred.

The existing `notes` object contains travel preferences keyed by form step, for
example `{"5": "Quiet places and slow travel"}`. No separate preferences field is
accepted. The converter forwards these preferences to all three specialists.
The original `companion`, free-text `budget`, and `constraints` remain unchanged.

Requests may omit `adults` and `currency` for storage. Serialized responses
include those fields with null defaults. Generation never infers counts or currencies
from text: `TaskConversionError` reports missing facts and trips without an
overnight stay. This MVP converter supports adult-only parties.

Use `from app.task_mapping import build_agent_tasks`, then
`tasks = build_agent_tasks(request)` to obtain `tasks.transport`, `tasks.stay`, and
`tasks.destination`. The converter performs no network calls or budget filtering.
The hotel pricing market retains StayTask's existing `AU` default; it is not a
request field. endDate means departure from the destination for the MVP. Initial
stay and destination windows must be aligned with the selected flight's actual
arrival by the orchestration layer. No database migration is required.


### Agent connection ownership

Each specialist owns its MCP connection for one run. Call
`await run_transport_agent(llm, task, settings=settings)`,
`await run_stay_agent(llm, task, settings=settings)`, or
`await run_destination_agent(llm, task, settings=settings)`.
The optional settings argument defaults to `get_settings()`; callers do not pass
an MCP client. Each connection closes on success, failure, or cancellation.
The accommodation endpoint is configurable with `TRIVAGO_MCP_URL` and defaults
to `https://mcp.trivago.com/mcp`. Tool adapters still accept initialized clients.

## Research API

`POST /research` runs the three real specialist agents synchronously and returns
`ResearchResult`: request, transport candidates, planning flight ID, hotel dates,
stay candidates, and destination candidates. It does not persist a trip or produce
the final v1 itinerary. `/trips` retains its existing persistence behavior.
Use the request example in [docs/schema](../docs/schema/README.md), including
explicit `adults` and `currency`. `?max_tool_turns=4` controls each agent's tool
rounds (1-8). Nested trace arrays are excluded from the response.

Run the existing FastAPI application and use `/docs` or send JSON to `/research`.
Uvicorn INFO logs include a research ID, agent names, tool arguments, full tool
observations, nonempty model content after each tool round, repair events, completion,
and errors. The response header
`X-Research-Id` matches the log ID. Configured API keys are redacted.
`RESEARCH_TIMEOUT_S` defaults to 600 seconds; allow sufficient client timeout.
The existing application startup still requires PostgreSQL.

Errors use `detail.code`, `detail.message`, and `detail.request_id`: 422 for
invalid generation input, 503 for missing server configuration, 409 for no usable
plan, 502 for known model/data-source failures, 504 for the overall timeout,
and 500 for unexpected failures. Request-shape errors use FastAPI's standard 422
validation response. This endpoint has no runtime mock or fallback data.
