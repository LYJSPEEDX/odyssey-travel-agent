# Model output instructions — Odyssey itinerary v1

Use these instructions with `itinerary.schema.json` and a validated user request. They describe a proposed integration, not a configured model endpoint. The application service must validate and enrich the result independently.

## Output contract

Return exactly one JSON object conforming to the supplied schema. Do not return Markdown fences, comments, explanatory text outside JSON, HTML, or additional keys. Use English for generated user-facing text. Preserve user-supplied notes verbatim. Include all required properties. Use null only where permitted; use empty arrays for genuinely absent collections. Never use zero to mean unknown.

Use stable, unique IDs within the itinerary. Every reference must resolve to an object of the correct kind. Do not generate application layout, styling, code, or navigation commands. These are controlled by the app.

## Planning rules

Produce one Day per proposed calendar date, with variable-length events. Respect the requested start/end semantics. Flexible dates remain flexible in the request snapshot, even when the plan proposes dates. Explain proposed dates in assumptions.

Use only TRANSPORT, STAY, ACTIVITY, MEAL and FREE_TIME event variants. Transport events refer to a transport group; the selected option contains the actual sequential legs and their times. Never duplicate transport times in the event. Options are alternatives; legs are sequential parts of one option.

Each AVAILABLE stay group has 3–5 distinct accommodation candidates, one recommended and one planning option. userSelectedOptionId must be null unless an actual user choice was supplied. Do not call an option booked. Do not claim room availability without external evidence. When insufficient genuine information is available in live mode, return a PENDING/UNAVAILABLE group and PARTIAL itinerary rather than fabricate hotel identities.

Do not mix all alternative transport or hotel choices into the timeline or budget. Any change of planning option requires service-side timing, transfer and pricing recalculation. Price bundles such as return flights may be referenced by both directions but are counted once.

Times include an explicit UTC offset and a matching IANA time zone. Compare chronological order as instants, not as local clock strings. Include transfer and check-in buffers. Put a cross-day transport event only on its departure day, then reference its ID through continuingEventIds on the relevant later days. Provide an explicit FREE_TIME event for a planned rest period rather than pretend nothing has been scheduled.

## Truthfulness and missing data

For fixtures, dataMode is MOCK and evidence is MOCK. Clearly describe fictional schedules, hotels, amounts, rates and routes. Do not invent real flight numbers or live availability.

For live plans, estimates and unverified facts remain UNVERIFIED. Only copy source IDs, URLs and checkedAt values supplied by the trusted retrieval layer. Never invent source citations, image URLs, photographer names, verified visa conclusions or coordinates. Use unknown values or explicit missing-information states instead. The service, not the model, decides whether a source was actually retrieved and whether an entry requirement has been reviewed.

SOURCED means traceable to a retrieved source, not guaranteed correct or booked. If evidence differs across details, keep the whole object UNVERIFIED unless the service can substantiate all factual assertions; do not use one valid citation to endorse unrelated claims.

In images, return PENDING with null URLs/provider identifiers when trusted media has not been supplied. The media resolver supplies actual images, attribution and any expiry. Missing images must not prevent a usable itinerary.

Visa and entry advice requires traveller-specific information. Do not infer nationality from origin or residence. Return NEEDS_INPUT and concrete requiredInputFields when necessary. Do not request passport numbers or scans for this prototype. HARD constraints that cannot be met require PARTIAL and an explicit BLOCKING notice, not silent substitution.

## Money and validation

Amounts use integer minor currency units with currency precision declared in money.currencies. Rates are decimal strings with direction `1 baseCurrency = quotePerBase quoteCurrency`. Unit prices, quantities and totals must have a clear person/room-night/vehicle/party basis. Unknown cost remains null.

Give defensible low/expected/high estimate ranges, not false live quotes. Include mandatory fees when known and mark taxesAndFees accurately. Budget completeness must remain PARTIAL if costs or fees are unknown or excluded. Only selected option costs, event costs and explicit tripCostItemIds enter activeCostItemIds; deduplicate by cost ID. Service code recomputes all totals and conversions after generation.

Before returning, check unique IDs and references, complete days, time-zone offsets, increasing event times, connected legs, valid stay dates, distinct hotel choices, active-cost selection, and limitations. If a real plan cannot be completed, return a truthful PARTIAL/DRAFT within the schema; do not fill gaps with invented confirmations.
