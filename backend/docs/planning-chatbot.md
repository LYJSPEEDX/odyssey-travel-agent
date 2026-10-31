# Planning chatbot component

The chatbot lives in `app/planning/` and runs in the existing FastAPI application, on the same port and with the same PostgreSQL schema and `.env` as trips and research. It has its own contracts, tables, routes, model boundary, and deterministic workflow. No frontend files are changed by this implementation.

## Start and try it

From `backend/`:

```sh
uv sync
uv run uvicorn app.main:app --reload
```

Restart an already running server after changing `.env`. On startup, the shared metadata creates the additive `planning_sessions` and `planning_turns` tables if missing. Existing trip tables and records are not migrated or replaced. Swagger is at `http://localhost:8000/docs`, under **planning chatbot**.

The component reads the existing `DB_*` and `LLM_*` settings. `PLANNING_TIMEOUT_S` defaults to 90 seconds and bounds model interpretation, including one structured-output repair. Geocoding uses the existing Open-Meteo adapter with a 12-second HTTP timeout and at most three queries/five returned options. A turn can therefore take longer than the model timeout. It does not need a Google Maps key, Kiwi, trivago, or flight/hotel research to chat. Those services remain relevant to subsequent itinerary generation.

Run the backend-only interactive client:

```sh
uv run python -m scripts.planning_chat
```

At each step, type a travel preference or answer. When the assistant proposes structured facts, inspect them and enter `:confirm 1` (or another displayed option number). Other commands:

- `:step ORIGIN` discusses a change to an earlier step. It does not silently confirm it.
- `:finalize` validates and exports the brief and generation request.
- `:delete` removes this session and its turn history.
- `:quit` leaves the session stored. No itinerary is generated automatically.

For an API-managed session, resume the CLI with `--session-id UUID --session-header ORIGINAL_OWNER_TOKEN`. The client does not print or store an automatically generated owner token; use a caller-supplied token if CLI restoration across runs is needed. Treat that token as private.

## HTTP workflow

All calls must retain the same `X-Session-Id` value. If omitted, the existing middleware mints a new owner token and echoes it in the response. Failing to retain it will make later calls appear to own a different session. A profile UID is not authorization.

### 1. Create

`POST /v1/planning-sessions`:

```json
{
  "schemaVersion": "1.0",
  "requestId": "78a2d8cc-8faf-49aa-9525-a5bc1393a668",
  "profile": {
    "residentialLocation": "Sydney, Australia",
    "displayCurrency": "AUD",
    "travelPreferences": "Food, culture, relaxed pace"
  }
}
```

The profile is optional. Creation makes no model call. It returns `sessionId`, revision 0, the active question, all eight step states, and `generationReadiness`. Reusing the creation request ID with the same owner/profile returns the same session; changing the profile under that ID returns 409. Creation replay returns the session's current snapshot.

The optional profile contract accepts current handoff fields including UID, name, gender, birthday, nationality, residence, currency/source, tags, and legacy preferences. These are suggestions, not confirmed travel facts. Only residence, currency, tags, and travel preferences are forwarded to the model. Name, UID, birthday, gender, nationality, and age are not sent to it. The optional complete profile snapshot is persisted, so omit unnecessary personal fields.

### 2. Chat

`POST /v1/planning-sessions/{sessionId}/turns`:

```json
{
  "requestId": "92eb5e58-8f31-4814-a5f1-c879d58b7958",
  "baseRevision": 0,
  "stepId": "ORIGIN",
  "action": "CHAT",
  "text": "I will depart from Sydney, Australia."
}
```

`CHAT`, `REFINE`, and `SUBMIT` are all conversational actions: they never confirm an answer. `SUBMIT` deliberately does **not** retain the old mock service's implicit confirmation behavior.

The model returns a step, reply, and proposed structured facts or clarification. Location proposals are resolved server-side by Open-Meteo; model-provided coordinates/place identifiers are not accepted. Ambiguous matches are offered as separate options. A lookup is city resolution, not verification of airports, prices, suitability, or availability. If lookup fails, confirmed session data remains unchanged.

The response outcome is `CLARIFY` or `PROPOSE`. For a proposal, save its `proposal.id`, selected `options[n].id`, `stepId`, and the new session revision. Each option includes the structured answer for display. The client should show that interpretation before confirming it.

### 3. Confirm

Send another turn with a **new** request ID, the latest revision, and IDs returned by the proposal:

```json
{
  "requestId": "025f1c51-2baf-4f52-8906-42ef47feac30",
  "baseRevision": 1,
  "stepId": "ORIGIN",
  "action": "CONFIRM",
  "proposalId": "COPY_CURRENT_PROPOSAL_UUID",
  "optionId": "COPY_SELECTED_OPTION_UUID"
}
```

The two placeholder values must be replaced with actual UUIDs. The controller verifies the proposal, option, ownership, revision and answer schema before applying it. It returns `CONFIRMED` and the next active question. A model cannot issue confirmation or choose navigation.

The eight steps are `ORIGIN`, `DESTINATION`, `DATES`, `COMPANIONS`, `BUDGET`, `CONSTRAINTS`, `FOLLOW_UP_1` (pace), and `FOLLOW_UP_2` (experiences). All prior steps must be confirmed before proceeding. To change a previous answer, chat at that earlier step, then explicitly confirm its replacement. Later confirmed answers are retained but marked `NEEDS_REVIEW`; they need fresh proposals and confirmation.

Useful inputs for a complete test:

1. Sydney, Australia.
2. Osaka, Japan.
3. Fixed dates: depart 2026-12-10, leave the destination 2026-12-14.
4. Exactly two adults and no children, travelling as partners.
5. AUD 5000 for the whole party and whole trip, including flights.
6. No special requirements (or specify whether each requirement is essential or preferred).
7. Relaxed pace with regular breaks.
8. Local food and cultural experiences.

The model can ask follow-up questions; this is expected when facts are ambiguous. Never automatically confirm a proposal in a user-facing client.

### 4. Restore or finalize

`GET /v1/planning-sessions/{sessionId}` restores the authoritative state, current revision, bounded message history and current proposal.

`POST /v1/planning-sessions/{sessionId}/finalize`:

```json
{"baseRevision": 16}
```

Use the actual latest revision, not a hardcoded 16. Success returns `brief` and `generationRequest`. The latter validates as the existing `TripRequest` and can be submitted to `/trips/preview` or `/trips` separately. The export retains explicit adults, currency, budget scope/flight inclusion, requirement strengths and pace/experience notes.

Finalization is a read-only export of that revision: it does not freeze the session, call the itinerary agents, create a trip, or start a job. Profile identity/demographics and the entire chat transcript are not forwarded to the itinerary agents. Only the confirmed facts/preferences mapped into the request are handed off.

The current generator supports adult-only, fixed-date, overnight trips. Flexible dates and child parties can be discussed and confirmed as planning facts, but readiness stays false and finalization returns 422 until supported facts are supplied. Maximum date range is 60 inclusive calendar days. Budget descriptions are not a guarantee of cost enforcement by research agents.

### 5. Delete

`DELETE /v1/planning-sessions/{sessionId}` returns 204 and cascades deletion of stored turns. Other owners receive 404 for session operations.

## Reliability and errors

A turn is reserved in the database before model invocation; no database transaction or row lock is held while waiting for the model. State and the completed response are committed atomically with an optimistic revision check.

- Same turn `requestId` and payload: replay the stored result without another model call. An old replay is the original snapshot; restore the session for its current revision.
- Same ID, different payload: 409 `REQUEST_ID_REUSED`.
- Same ID still processing: 409 `TURN_PROCESSING`; do not automatically issue another model request.
- Stale revision, stale proposal, locked step: 409.
- Invalid body or unsupported finalization: 422.
- Missing model configuration: 503.
- Model timeout: 504.
- Model/lookup failure: 502 with a generic message. Provider credentials and raw exceptions are not returned.

Failures are recorded for replay, while confirmed session state is unchanged. After inspecting/restoring the session, an explicit retry uses a **new** request ID. Concurrent different actions may both invoke the model, but only a response based on the current revision can commit. A hard process crash can leave a reserved turn PROCESSING; there is no background recovery worker or paid-call exactly-once guarantee. Restore and explicitly retry if necessary. Do not interpret processing status as an itinerary generation job.

## Tests

Deterministic component tests, including mocked provider responses:

```sh
uv run python -m unittest discover -s tests -p 'test_planning.py' -v
```

Include PostgreSQL route, replay, ownership and stale-response tests (test data is rolled back; initialize new tables by starting the shared application first):

```sh
RUN_DB_TESTS=1 uv run python -m unittest discover -s tests -p 'test_planning.py' -v
uv run ruff check app/planning scripts/planning_chat.py scripts/planning_smoke.py tests/test_planning.py
```

Optional live smoke test, using the running API, configured model and geocoder:

```sh
uv run python -m scripts.planning_smoke --live
```

It explicitly confirms synthetic example facts, exports generation input, and deletes its temporary session in `finally`. It consumes model/provider usage but does not call research or generate an itinerary. A clarification response causes it to stop rather than guess. Production clients must obtain confirmation from the human.

## Integration boundaries and remaining concerns

- Android is unchanged and still calls its local mock. This API uses its own versioned contract: string `schemaVersion`, flattened text/action fields, proposal IDs and explicit confirmation. A future Android adapter must translate the existing local protocol rather than substitute the URL alone.
- Itinerary generation now emits the shared frontend v2 contract; the Android HTTP adapter and generation-job/SSE APIs remain separate work. They are not necessary to exercise this standalone chatbot.
- Anonymous ownership follows existing backend conventions. It is not account authentication. Add authentication, per-owner rate limits/quotas, retention/expiry, abandoned-turn recovery and deployment controls before exposing it as a public service. Sessions/turns currently persist until explicitly deleted; message context is bounded to 16 entries per step, but stored turn records are retained for replay.
- The model can still misunderstand a user or suggest a poor destination. Structured validation prevents malformed facts and invalid transitions, not semantic errors; explicit confirmation is required.
