# Android live HTTP demo

The home **Create my trip** banner opens the live planner. **Try the offline demo** opens
the existing local mock flow. The app never falls back to mock planning after a network failure.

## Run a live demo

1. In `backend`, configure `.env` for PostgreSQL, your LLM, and Google Maps access. Install
   dependencies (`uv sync`), then start `uv run uvicorn app.main:app --host 0.0.0.0 --port 8000`.
   Startup must succeed; `/healthz` must report both server and database `ok`.
2. Build/install the Android debug app from `Frontend` (`./gradlew :app:installDebug`).
3. In Settings → **Live demo server**, use `http://10.0.2.2:8000` for an Android emulator.
   On a phone, use the computer's LAN IP, e.g. `http://192.168.1.20:8000`, on the same trusted
   Wi-Fi network. Allow the backend port through the local firewall. No trailing `/docs`.
4. Open Create my trip → **Check connection** → **Start live planning**. The health check
   checks reachability/database, not model/provider credentials or search availability.
5. Send a message for each step. Read the returned structured proposal and explicitly tap
   **Confirm this answer**. A chat response never silently confirms facts. Earlier answers
   can be reviewed/revised; backend revision and proposal IDs prevent stale confirmations.
6. Review the full confirmed trip and select **Generate and save itinerary**. This uses the
   backend's configured model/provider quota. Wait on the live screen: generation can take
   several minutes. On success, open the itinerary or find it in My Trips.

Suggested messages (adapt dates before presenting):

- Depart Sydney, Australia.
- Destination Osaka, Japan.
- Depart 2026-12-10; leave the destination on 2026-12-13. The date-picker helper can compose
  this message; it still requires Send and explicit proposal confirmation.
- Exactly two adults, no children, travelling together.
- AUD 5000 for the entire party and whole trip, including flights.
- No extra requirements.
- A relaxed pace with regular breaks.
- Parks, local food and cultural experiences.

Flexible dates, child parties and same-day trips cannot generate with the current backend.
Budget scope, currency, adult count and flight inclusion must be explicit. A model may ask
for clarification. Confirm only facts the human agrees with; availability or provider failure
can still prevent generation. No booking is made and budgets remain partial estimates.

## Network and lifecycle behaviour

- Uses the independent `/v1/planning-sessions` contract; it does not send mock-protocol JSON.
- `X-Session-Id` is generated/persisted before the first request and scoped to the configured
  endpoint. Profiles contribute only city/country, currency, travel tags and preferences.
  Names, birth dates, gender and nationality are not sent by the Android live adapter.
- Session/revision, immutable creation request and pending turn IDs are persisted in private
  preferences excluded from backup. Drafts are per step. Rotation and screen navigation reuse
  the in-process operation; network work does not retain an Activity or duplicate generation.
- An interrupted planning turn offers **Check original reply**, which replays the exact same
  request ID/payload and then restores the latest state. This relies on backend turn replay.
  A failed/abandoned turn only gets a new ID on a new explicit user submission. Restoring the
  session does not automatically submit a message or confirm a proposal.
- No automatic mutation retry or HTTP redirect forwarding. Connection timeout: 15 seconds;
  planning timeout: 330 seconds; generation response timeout: 20 minutes. Long generation
  shows an indeterminate loader, not simulated stages/percentages or SSE.
- `/trips` is not idempotent and has no generation-job/status recovery API. A persisted marker
  is written before the POST. A lost/invalid response or interrupted process leaves the outcome
  uncertain. **Check saved server trips** lists up to the latest 100 owned trips; choose one to
  fetch/validate/save its itinerary. This does not retry generation. Listing may not show a still
  running request. Starting another generation requires explicit acknowledgement of possible
  duplicate trips and provider usage. Matching is by a human-selected trip, not a guessed result.
- A valid response is parsed using `SavedTrip.fromContract`, then saved off the UI thread using
  the returned server itinerary ID. A local-save failure can retry saving without regenerating.
- Background operation is best effort while the process lives; no foreground service or durable
  worker is promised. Keep the app visible for the presentation. If Android kills it, restore
  planning/check server trips. There is no paid-call exactly-once guarantee.
- New plan resets the active device draft but preserves old server sessions/trips. Clearing app
  data loses anonymous ownership and does not delete remote data. No login or cross-device sync
  is implemented. Server cleanup/retention remains an operational responsibility for the demo.

Debug builds allow cleartext HTTP for local development; use a trusted demo network.
Release builds keep cleartext disabled and require a configured HTTPS URL. No API keys go in
Android; model/provider credentials remain in backend `.env`. Settings reject embedded URL
credentials, query strings and fragments. Anonymous sessions are not public-service authentication.

## Deterministic HTTP verification (not a live travel demo)

The test-only server uses real planning contract/confirmation logic and the real itinerary
assembler with synthetic research. It never calls an LLM/provider/database and marks returned
itineraries MOCK. Do not point a presentation at it while claiming real travel results.

From backend:

```sh
uv run uvicorn android_http_server:app --app-dir tests --host 127.0.0.1 --port 8765
```

With an emulator running, from Frontend:

```sh
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.odyssey.LiveHttpDemoTest,com.example.odyssey.HomeFlowTest,com.example.odyssey.FrontendItineraryV2Test
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The HTTP test sends/explicitly confirms all eight steps, rotates during a turn/generation,
checks generation POST counts across rotation and lost responses, validates local saving and server-trip recovery, and restores the
persisted session with the same owner identity. It temporarily uses host port 8765 and restores
its settings afterwards. Real LLM, PostgreSQL and provider availability need a separate live rehearsal.
