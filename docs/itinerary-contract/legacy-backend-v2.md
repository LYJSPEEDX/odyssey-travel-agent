# Archived backend MVP contract

Historical reference only. No longer emitted. Version 2.0 without the frontend contractId is unsupported by Android and the itinerary read endpoint. Stored documents remain unchanged.

# Odyssey Itinerary JSON Protocol v2

Status: **Implemented MVP**. `POST /trips` generates and persists the v2 itinerary; `POST /trips/preview` generates it without persistence. This version is not compatible with the v1 itinerary parser.

## Purpose

Return the travel information currently available from the research agents and scheduling agent. The assembler converts the research and schedule results into the contract below. Unknown information stays `null` or is omitted only where explicitly allowed; it must not be invented.

## API response

The generation API returns only the itinerary object as its JSON response body. It does not return the raw `research` or `schedule` objects. Errors use a separate API error response and are not itinerary objects.

```json
{
  "schemaVersion": "2.0",
  "id": "itinerary_001",
  "generatedAt": "2026-10-28T09:00:00Z",
  "request": {
    "departure": "Sydney",
    "destination": "Osaka, Japan",
    "startDate": "2026-12-10",
    "endDate": "2026-12-14",
    "companion": "Couple",
    "budget": "AUD 6000 for two adults for the entire trip",
    "adults": 2,
    "currency": "AUD",
    "constraints": [],
    "notes": {
      "5": "We enjoy local food, traditional culture, parks, and a relaxed pace."
    }
  },
  "flights": {
    "planningFlightId": "flight_001",
    "rationale": "This flight was selected for the initial schedule.",
    "options": [
      {
        "id": "flight_001",
        "price": {"currency": "AUD", "amountMinor": 332200},
        "bookingUrl": null,
        "baggage": {"personalItem": 2, "cabinBag": 2, "checkedBag": 0},
        "outbound": {
          "departureAirport": "SYD",
          "arrivalAirport": "KIX",
          "departure": "2026-12-10T12:05:00+11:00",
          "arrival": "2026-12-10T19:55:00+09:00",
          "durationMinutes": 590,
          "stops": 0,
          "segments": [
            {
              "departureAirport": "SYD",
              "arrivalAirport": "KIX",
              "departure": "2026-12-10T12:05:00+11:00",
              "arrival": "2026-12-10T19:55:00+09:00",
              "departureTimeZone": "Australia/Sydney",
              "arrivalTimeZone": "Asia/Tokyo",
              "airline": "Jetstar Airways",
              "flightNumber": "JQ13",
              "cabin": "Economy",
              "durationMinutes": 590
            }
          ]
        },
        "inbound": {
          "departureAirport": "KIX",
          "arrivalAirport": "SYD",
          "departure": "2026-12-14T21:00:00+09:00",
          "arrival": "2026-12-15T08:45:00+11:00",
          "durationMinutes": 585,
          "stops": 0,
          "segments": [
            {
              "departureAirport": "KIX",
              "arrivalAirport": "SYD",
              "departure": "2026-12-14T21:00:00+09:00",
              "arrival": "2026-12-15T08:45:00+11:00",
              "departureTimeZone": "Asia/Tokyo",
              "arrivalTimeZone": "Australia/Sydney",
              "airline": "Jetstar Airways",
              "flightNumber": "JQ14",
              "cabin": "Economy",
              "durationMinutes": 585
            }
          ]
        }
      }
    ]
  },
  "stays": {
    "checkIn": "2026-12-10",
    "checkOut": "2026-12-14",
    "planningStayId": "hotel_001",
    "rationale": "This hotel was selected for the initial schedule.",
    "options": [
      {
        "id": "hotel_001",
        "name": "Example Osaka Hotel",
        "city": "Osaka, Japan",
        "stars": 4,
        "reviewScore": 9.1,
        "reviewCount": 7341,
        "latitude": 34.6909,
        "longitude": 135.5069,
        "amenities": ["WiFi in rooms", "A/C"],
        "rooms": 1,
        "price": {"currency": "AUD", "amountMinor": 36500},
        "bookingUrl": null,
        "freeCancellation": true,
        "breakfastIncluded": false,
        "imageUrl": null
      }
    ]
  },
  "places": [
    {
      "id": "place_001",
      "name": "Example Market",
      "providerPlaceId": "google_place_id",
      "latitude": 34.6654,
      "longitude": 135.5068,
      "googleMapsUrl": "https://www.google.com/maps/place/example"
    }
  ],
  "days": [
    {
      "date": "2026-12-11",
      "dayNumber": 1,
      "timeZone": "Asia/Tokyo",
      "items": [
        {
          "id": "item_001",
          "type": "MEAL",
          "title": "Explore the market",
          "description": "Try local food and confirm opening hours before visiting.",
          "startTime": "09:30:00",
          "endTime": "11:00:00",
          "timingStatus": "ESTIMATED",
          "placeId": "place_001"
        },
        {
          "id": "item_002",
          "type": "FREE_TIME",
          "title": "Take a break",
          "description": "Allow time for a relaxed walk and rest.",
          "startTime": "11:00:00",
          "endTime": "11:30:00",
          "timingStatus": "ESTIMATED",
          "placeId": null
        }
      ]
    }
  ],
  "budget": {
    "currency": "AUD",
    "items": [
      {"type": "FLIGHT", "label": "Return flights", "amountMinor": 332200, "currency": "AUD"},
      {"type": "STAY", "label": "Accommodation", "amountMinor": 36500, "currency": "AUD"}
    ],
    "knownTotalMinor": 368700,
    "isComplete": false
  },
  "notes": {
    "transport": "Flight prices and availability may change.",
    "stay": "Confirm room conditions and final price before booking.",
    "destination": "Confirm opening hours and suitability before visiting.",
    "schedule": "Activity times are estimates."
  }
}
```

The values above are illustrative, not real offers. The `days` array is abbreviated to one day for readability; an actual result includes all destination schedule days.

## Fields and sources

All keys shown in the example are required unless noted below. Empty arrays remain arrays; unknown nullable values are `null`.

| Field | Source and meaning |
| --- | --- |
| `schemaVersion` | Fixed string `2.0`. |
| `id` | Unique ID assigned to this generated itinerary. It does not imply persistence. |
| `generatedAt` | Generation time as an ISO 8601 timestamp with an offset. |
| `request` | The submitted Trip Request. `notes` remains a key-value object; `constraints` remains a list. The generation pipeline requires `adults` and `currency`. |
| `flights.options` | Transport Agent candidates. Each ID identifies one return-flight offer in this result. |
| `flights.planningFlightId` | The candidate selected by the orchestrator for scheduling. This is not a booking or a user selection. |
| `flights.rationale` | Transport Agent rationale. It describes its candidates and need not explain why the orchestrator selected a particular flight. |
| `stays.options` | Stay Agent candidates, including the best deal selected for each candidate. `rooms` is the number of searched rooms, not verified room capacity. |
| `stays.planningStayId` | The recommended hotel used by the current schedule. This is not a booking or a user selection. |
| `stays.rationale` | Stay Agent rationale. |
| `places` | Destination Agent candidates; assembler creates result-local IDs and maps the agent labels to them. |
| `days` | Destination-local schedule returned by the Schedule Agent. `dayNumber` starts at 1 for the first schedule date. Flights remain in `flights` and are not duplicated as day items. |
| `budget` | Prices for the selected planning flight and stay. It is a partial quote, not a complete trip price. |
| `notes` | The agents' rationale or relevant unverified details; do not expose internal traces. |

### Field rules

- IDs are non-empty strings. `planningFlightId` and `planningStayId` must refer to an option in their respective arrays. Every non-null day item `placeId` must refer to `places[].id`. IDs are stable only within one generated result.
- `request.startDate` is the outbound departure date. `request.endDate` is the date the return flight leaves the destination; arrival home may be later.
- `flights.options[].price` is the provider's whole-party return fare, not a per-person amount. `stays.options[].price` is the provider's total for the entire searched stay and all searched rooms. Prices are quotes, not confirmed bookings.
- Flight sector `departure` and `arrival` and segment timestamps include the local offset for their airport. Each segment also retains its IANA time zone. `durationMinutes` is derived from the source duration in seconds; stopovers are not counted as flight-segment duration.
- Each schedule item type is `ACTIVITY`, `MEAL`, or `FREE_TIME`. Schedule times are destination-local clock times and `days[].timeZone` identifies the destination time zone. The `timingStatus` is `ESTIMATED`. Activities and meals require a `placeId`; free time may use `null`.
- `bookingUrl`, `freeCancellation`, `breakfastIncluded`, `imageUrl`, `stars`, `reviewScore`, `reviewCount`, `latitude`, `longitude`, `places[].name`, and `googleMapsUrl` may be `null` when the source does not provide them. A null cancellation or breakfast value means unknown, not false. Place `name` comes from the source title and may be missing.
- Hotel price, booking URL and cancellation/breakfast attributes must come from the **same selected deal**. Do not combine the cheapest deal's price with another deal's URL or conditions. A selected deal's URL may be `null`.
- `amountMinor` uses the currency's minor unit: for example, AUD 3,322.00 is `332200`, while JPY uses whole yen. Values are non-negative integers.
- `budget.items` contains the selected flight and stay quote in their original currencies. `knownTotalMinor` sums only items in `budget.currency`. A different-currency item remains in the list and is excluded from that sum until a reliable exchange rate is available. `isComplete` is `false`; meals, activities, local transport and unverified fees are not included. Explain omitted currencies and other exclusions in `notes`.
- Source durations are rounded to the nearest minute, with half minutes rounded up. Date and time consistency, references, and currency arithmetic require validation before return.

## Changes from v1

| v1 | v2 MVP |
| --- | --- |
| `transportGroups` and `stayGroups` | Replace with flight and hotel candidate lists. |
| Place country, address and related required fields | Keep only place data currently supplied by the destination agent. |
| `media` and image-source management | Remove the independent media structure. A hotel `imageUrl` is nullable; its availability and usage conditions require confirmation. |
| `entryRequirements` and detailed traveler documents | Remove; retain the request's adult count. |
| Exchange rates and exchange advice | Remove; no currency conversion is performed. |
| Budget ranges, contingency and complex cost references | Replace with selected quotes and a same-currency known subtotal. |
| Activity categories, reservations and opening hours | Remove required fields; retain schedule title, description, local times and place reference. |
| Evidence, sources and detailed quality structures | Replace with short agent notes. No claim of verification is implied. |
| Revisions, explicit user selections and replanning | Defer until persistence and editing are implemented. |

## Implementation boundary

`POST /trips` runs research, scheduling, and assembly synchronously, validates the generated document against [the v2 JSON Schema](../../backend/app/itinerary-v2.schema.json), and saves a successful result. Its response body contains only the itinerary. `Location` identifies the saved trip; retain `X-Session-Id` for later reads. Generation or validation failure does not save a partial trip. `POST /trips/preview` runs the same generation pipeline without saving the result.

Validation also checks ID references, selected candidates and the known-price subtotal. The result does not verify actual availability, bookings, travel feasibility or every source claim. The v2 result is incompatible with the v1 client parser.
