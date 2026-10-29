# Android live AI demo

The guided questionnaire keeps local suggestions. Final generation calls `POST /trips/preview`; there is no automatic mock fallback. AI and Maps credentials stay in `backend/.env`.

## Run

1. Start PostgreSQL and configure the backend `.env` with database, model, and Google Maps access.
2. From `backend/`, run `uv sync`, then `uv run uvicorn app.main:app --reload`.
3. Open `Frontend/` in Android Studio and run the debug app on an emulator. The default backend URL is `http://10.0.2.2:8000`.
4. Complete the questionnaire using fixed departure and return dates (at least one overnight stay, no more than 60 calendar days).
5. Confirm the explicit adult count and search currency in the final Generate with AI dialog. This MVP supports adult-only parties. Budget tiers remain illustrative AUD/person/day; custom budget text is passed verbatim.
6. Allow several minutes for live research and scheduling. Successful results are validated, saved on the device, and opened with the existing itinerary screen.

For a physical phone, start Uvicorn with `--host 0.0.0.0` and build with a reachable LAN URL:

```sh
./gradlew :app:installDebug -PodysseyApiBaseUrl=http://YOUR_COMPUTER_LAN_IP:8000
```

HTTP is enabled only in the debug manifest. Release builds intentionally have no demo backend URL configured.

## Behavior and limits

- Rotation retains the running request without starting another AI call. If the process is killed, an unfinished request requires an explicit retry.
- Errors retain questionnaire answers and offer Retry generation or Edit my choices. Retries may make another paid AI request; cancelling on the device does not guarantee that upstream work has stopped.
- No bookings are made. Flights and hotels are provider quotes, activity timings are estimates, and room capacity, baggage weight, cancellation policies, and transfer details may be unknown.
- The v1 assembler includes the flight and hotel selected for the schedule. Full raw candidate lists remain in the preview response's research section. Round-trip airfare is counted once.
- Saved trips are local snapshots. The separate `/trips` persistence API still queues requests and has no generation worker.
- The itinerary’s “Ask AI to adjust” sheet remains an offline draft; only initial generation is connected.
- Itinerary v2 and the proposed `/v1/planning-sessions` API are not used.

## Checks

```sh
# backend/
RUN_DB_TESTS=1 uv run python -m unittest discover -s tests -v
# Frontend/
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.odyssey.LivePreviewContractTest,com.example.odyssey.PlannerFlowTest,com.example.odyssey.MockPlanningServiceTest,com.example.odyssey.TripPreviewClientTest
```

Backend tests use synthetic research and scheduling results; PostgreSQL tests roll back their changes. Android tests use synthetic responses and exercise the production v1 parser, rendering, and local saving without spending API credits.
