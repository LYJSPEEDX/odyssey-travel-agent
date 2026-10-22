# Profile → location recommendations → trip planning

## Scope and contract

The latest Android sources are at `root/odyssey-travel-agent/Frontend/`.
The shared contract is `root/odyssey-travel-agent/docs/profile/profile.schema.json`,
with definitions `profile`, `recommendationRequest`, and `recommendationResponse`.
This API uses those field names and response structure; it does not use the old
`tmp/` prototype's `homeCity`/destination-card format.

`POST /recommendations/locations` accepts a profile and returns **both departure
and destination suggestions**. No database migration, profile table, model API
key, or new dependencies are required. Profiles are not persisted by this endpoint.

## Workflow

1. RegistrationActivity collects and saves the profile locally using ProfileStore.
2. On “Generate my trip”/entry to trip planning, the future Android API client reads
   that profile and constructs a request using `ProfileContracts.recommendationRequest`.
3. Android calls POST `/recommendations/locations` with `origin: null` and
   `destinations: []` if the user has not supplied location choices.
4. Backend validates the profile, chooses a suggested departure, and returns destination
   suggestions with reasons. Android validates the response using
   `ProfileContracts.recommendationResponse` and displays the suggestions.
5. User confirms or edits the departure and destination. Suggestions must not silently
   replace confirmed form choices. The existing form supports one destination; choose
   one response item rather than sending the whole suggestions list to POST `/trips`.
6. User completes dates, companions, budget, and constraints. Android sends POST `/trips`
   with the confirmed location names and the remaining form answers.
7. Trip API stores a PENDING trip and QUEUED run. Generation is still unimplemented;
   do not keep polling expecting an itinerary in this version.
8. After a future worker produces a validated READY itinerary, Android can fetch
   `/trips/{id}/itinerary`, call `SavedTrip.fromContract`, save via TripStore, and open details.

Steps 2–6 describe frontend wiring still to be implemented. This backend change does
not modify Android navigation, profile persistence, or local mock generation.

## Request

```http
POST /recommendations/locations
Content-Type: application/json
X-Session-Id: <reuse the previous response header when available>
```

```json
{
  "schemaVersion": "1.0",
  "profile": {
    "schema_version": 1,
    "age": 25,
    "residential_location": "Sydney, Australia",
    "travel_preferences": "Museums, food, relaxed pace"
  },
  "origin": null,
  "destinations": []
}
```

All shown fields are required. Empty preferences use `""`; unknown origin uses null;
unknown destinations use an empty array. Age must be an integer from 1–120. Location
strings must be nonblank, at most 200 characters. Preferences are at most 2000
characters. At most 20 unique destinations are allowed. Text is trimmed; duplicate
choices after trimming/case folding are rejected. Unknown fields and wrong types
return 422, including string or boolean ages. Departure equal to an explicitly
supplied destination also returns 422. No database write occurs.

## Response

HTTP 200, JSON body (example; the ruleset currently returns three suggestions when
no destinations are supplied):

```json
{
  "schemaVersion": "1.0",
  "departure": {
    "location": "Sydney, Australia",
    "reason": "Suggested from your residential location; confirm where you will actually depart."
  },
  "destinations": [
    {
      "location": "Kyoto, Japan",
      "reason": "Demo catalog suggestion matching preference keywords: museum, food, relax. Confirm before planning; no live data was checked."
    },
    {
      "location": "Melbourne, Australia",
      "reason": "Demo catalog suggestion matching preference keywords: food, museum. Confirm before planning; no live data was checked."
    },
    {
      "location": "Singapore",
      "reason": "Demo catalog suggestion matching preference keywords: food. Confirm before planning; no live data was checked."
    }
  ]
}
```

The response includes X-Session-Id via existing middleware. Retain it for trip requests.
There is no profile ID and no authenticated account. The response does not create a trip.

## Suggestion rules and limitations

- A supplied origin is retained. Otherwise the residential location becomes a departure
  suggestion requiring confirmation, not a verified airport or confirmed trip origin.
- Supplied destinations are retained with confirmation reasons; they are not replaced
  by the catalog. Names are not geocoded or validated against a place service.
- Otherwise a five-location demo catalog is ranked by case-insensitive preference
  keyword matches. Ties retain catalog order; the first three are returned, excluding
  a departure with the same city label. This is a simple text check, not place resolution.
- Age is accepted and validated for contract compatibility; it currently does not affect
  ranking. There are no age suitability, visa, safety, availability, or price assessments.
- No LLM or external data service is called. Replace `suggest_locations` with a provider
  implementation later while keeping the public response contract and validating output.
- Residential text must not be interpreted as citizenship, passport details, or residence
  country. Location names must be resolved to place IDs before building a final itinerary.
- POST `/trips` still accepts the five-step form only. The recommendation request's profile
  is not copied into a trip or forwarded to a future generation worker in this version.

## Mapping into POST /trips

Use the user-confirmed `departure.location` as `departure` and the selected
`destinations[i].location` as `destination`. Add `flexibleDates`, ISO `startDate`/
`endDate`, `companion`, `budget`, `constraints`, and submitted step `notes` as described
in the backend README. Do not send recommendation reasons or the profile envelope to
POST `/trips`.

## Manual verification

Start FastAPI and open `/docs`; select recommendations → POST `/recommendations/locations`
and paste the request above. It should return 200 with a departure and three destinations.
Try a known origin and one chosen destination and confirm both are retained. An age of
0, a blank residence, duplicate destinations, or an unsupported schema version returns 422.
The frontend emulator base URL is `http://10.0.2.2:8000`; local Swagger uses
`http://localhost:8000/docs`. Android still needs networking and development HTTP setup.

## Automated verification

From the backend folder:

```sh
uv run python -m unittest discover -s tests -v
uv run ruff check app tests
```

Recommendation tests exercise the HTTP route without PostgreSQL, including strict profile
validation, unknown choices, known choices, keyword ranking, departure exclusion, and
session headers. Three existing PostgreSQL tests remain opt-in with `RUN_DB_TESTS=1`.
