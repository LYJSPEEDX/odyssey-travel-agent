# Local profile (version 1)

RegistrationActivity is the launcher. First launch collects a profile before opening HomeActivity; later launches skip registration when a valid profile exists. No username, password, network service, or location permission is required. The existing Settings nickname remains a separate local display preference.

The UTF-8 JSON file is `profile.json` in Android's private `Context.getFilesDir()` directory (normally `/data/user/0/com.example.odyssey/files/profile.json`). It is generated from the submitted form, not bundled with invented personal data.

```json
{
  "schema_version": 1,
  "age": 25,
  "residential_location": "Sydney, Australia",
  "travel_preferences": "Museums, food, relaxed pace"
}
```

- `schema_version`: integer, currently 1.
- `age`: required integer from 1 to 120.
- `residential_location`: required manually entered location, trimmed, at most 200 characters.
- `travel_preferences`: optional free text, trimmed, at most 2000 characters; saved as an empty string when omitted.

ProfileStore uses Android AtomicFile on a background executor. Failed writes keep the form open for retry. An unreadable or unsupported profile displays an explanation and allows the user to submit a replacement; reading alone never removes data. After saving, registration finishes so Back does not return to the form. Android restores form values on activity recreation. Data stays local and follows the existing app backup policy; uninstalling or clearing app data removes it.

Verification: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` and, with a device, `./gradlew :app:connectedDebugAndroidTest`. ProfileStoreTest covers persistence, optional preferences, invalid data, and damaged-file recovery. Manual flow: fresh install → required-field errors → save → Home → relaunch → Home; also rotate with entered text and check dark theme and keyboard scrolling.

## Shared validation and location recommendations

`profile.schema.json` is the source of truth for three separate contracts, composed with local `$defs` references. Its default root still validates the version-1 saved profile, so existing files keep their field names and need no migration.

- `profile`: required age and residential location; optional preferences are represented by `""`.
- `recommendationRequest`: required profile, `origin: null` when the departure is unknown, and `destinations: []` when no destinations have been chosen. Known choices are nonblank location names, not resolved place IDs.
- `recommendationResponse`: a nonblank departure location and one or more destination recommendations, each with a nonblank reason. These are suggestions awaiting user confirmation.

`ProfileContracts.recommendationRequest(context, profile, origin, destinations)` creates and validates an independent request snapshot. `ProfileContracts.recommendationResponse(context, raw)` validates a model response before use. The included templates demonstrate the formats. This change defines the integration boundary; it does not call a model service or automatically change the planner's selections.

Both profile storage and recommendation payloads reuse `ItineraryJson.preflight()` and `ItineraryShape.check()`. Profile storage now rejects wrong JSON types, unknown fields, missing fields, overlong strings, and whitespace-only locations rather than coercing values through `getInt()`/`getString()`. Reads have a byte limit before JSON parsing. The shared checker enforces schema `maxLength`, counts Unicode code points, and searches JSON Schema patterns unless the pattern explicitly uses anchors. It remains a limited structural validator; backend validation should use a full JSON Schema implementation.

For a backend validator, load this schema as the reference root and validate using `{"$ref":"#/$defs/recommendationRequest"}` or `{"$ref":"#/$defs/recommendationResponse"}`. Keep the existing itinerary schema and its business checks unchanged. Once users confirm suggestions and locations are resolved, map them to the itinerary request's `originPlaceId` and `destinationPlaceIds`. Age can populate the existing traveler `age`; residential city text must not be treated as `residenceCountry`, citizenship, passport information, or a confirmed departure.

`ProfileContractsTest` checks the shared string rules, strict profile types, incomplete trip requests, response requirements, and malformed model JSON. `ProfileStoreTest` also checks that malformed saved fields do not get coerced or silently overwritten.

The separate, pre-existing prototype under `tmp/` currently exposes a destination-only API with `homeCity`, `interests`, and `notes`, and returns four destination cards. The new departure-and-destination contract is not a replacement for that API and is not sent to it. A future connector must explicitly adapt that provisional API or extend it; these changes preserve its existing inputs, output, and tests.
