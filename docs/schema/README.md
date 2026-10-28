# TripRequest

Request body for `POST /trips` (storage) and `POST /research` (live research). Definition: [schemas.py](../../backend/app/schemas.py).

| Field | Type | Meaning / default |
|---|---|---|
| `departure` | string | Departure location; required. |
| `destination` | string | Destination; required. |
| `startDate` | date | Departure date, `YYYY-MM-DD`; required. |
| `endDate` | date | End date, `YYYY-MM-DD`; required. The MVP interprets it as departure from the destination. |
| `companion` | string | Companion description, e.g. `Couple`; required. |
| `adults` | integer or null | Adult count, 1-20; required for generation. |
| `currency` | string or null | Three-letter currency code, normalized to uppercase; required for generation. |
| `budget` | string | Free-text budget description; required. |
| `constraints` | string[] | Travel restrictions; defaults to `[]`. |
| `notes` | object | Travel preferences keyed by form step; defaults to `{}`. |

`notes` keys are strings `"1"` through `"5"`; values are nonblank preference strings.
The converter reads values in step order and forwards them as preferences to all three agents.
There is no separate request-level `preferences` field.

Date ranges contain 1-60 calendar days, inclusive. Both dates are required and
non-null. MVP generation requires an overnight stay and explicit `adults` and
`currency`. Unknown fields are rejected.

## Example

```json
{
  "departure": "Sydney",
  "destination": "Kyoto, Japan",
  "startDate": "2026-12-10",
  "endDate": "2026-12-14",
  "companion": "Couple",
  "adults": 2,
  "currency": "AUD",
  "budget": "AUD 6000 for the entire party and whole trip",
  "constraints": ["Travelling with a dog", "Avoid long layovers"],
  "notes": {
    "5": "Quiet places, nature, and slow travel"
  }
}
```
