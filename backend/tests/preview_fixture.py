"""Synthetic provider facts for preview tests; no external services or credentials."""

from app.agents.orchestrator import ResearchResult
from app.agents.schedule import ScheduleResult


def fixture():
    def sector(origin, destination, departure, arrival, departure_tz, arrival_tz):
        return dict(
            from_code=origin,
            to_code=destination,
            departure=departure,
            arrival=arrival,
            duration_s=36000,
            stops=0,
            segments=[
                dict(
                    from_code=origin,
                    to_code=destination,
                    from_city=origin,
                    to_city=destination,
                    from_name=origin + " Airport",
                    to_name=destination + " Airport",
                    departure=departure,
                    arrival=arrival,
                    departure_tz=departure_tz,
                    arrival_tz=arrival_tz,
                    duration_s=36000,
                    carrier="XX",
                    carrier_name="Example Air",
                    flight_number="XX123",
                    cabin="Economy",
                )
            ],
        )

    flight = dict(
        id="provider-flight",
        price_minor=200000,
        currency="AUD",
        total_duration_s=72000,
        booking_url=None,
        baggage={},
        outbound=sector(
            "SYD",
            "KIX",
            "2026-12-10T08:00:00+11:00",
            "2026-12-10T16:00:00+09:00",
            "Australia/Sydney",
            "Asia/Tokyo",
        ),
        inbound=sector(
            "KIX",
            "SYD",
            "2026-12-12T20:00:00+09:00",
            "2026-12-13T08:00:00+11:00",
            "Asia/Tokyo",
            "Australia/Sydney",
        ),
    )
    research = ResearchResult.model_validate(
        dict(
            request=dict(
                departure="Sydney",
                destination="Osaka",
                startDate="2026-12-10",
                endDate="2026-12-12",
                companion="Couple",
                adults=2,
                currency="AUD",
                budget="AUD 4000",
                notes={"5": "Food and culture"},
            ),
            transport=dict(candidates=[flight], rationale="Direct flights", searches=1, trace=[]),
            planning_flight_id="provider-flight",
            check_in="2026-12-10",
            check_out="2026-12-12",
            stay=dict(
                candidates=[
                    dict(
                        rooms=1,
                        offer=dict(
                            id="provider-hotel",
                            name="Example Hotel",
                            city="Osaka, Japan",
                            stars=None,
                            review_score=None,
                            review_count=None,
                            latitude=None,
                            longitude=None,
                            distance_text=None,
                            amenities=["Wi-Fi"],
                            image_url=None,
                            deals=[
                                dict(
                                    advertiser="Example",
                                    currency="AUD",
                                    price_per_night_minor=20000,
                                    total_minor=40000,
                                    url=None,
                                    free_cancellation=None,
                                    breakfast_included=None,
                                    non_refundable=None,
                                )
                            ],
                        ),
                    )
                ],
                recommended_id="provider-hotel",
                rationale="Near the sights",
                rooms=1,
                searches=1,
                trace=[],
            ),
            destination=dict(
                candidates=[
                    dict(
                        label="P1",
                        place=dict(place_id="provider-place", source_title="Example Park"),
                    )
                ],
                rationale="A park visit",
                searches=1,
                trace=[],
            ),
        )
    )
    schedule = ScheduleResult.model_validate(
        dict(
            days=[
                dict(
                    date=day,
                    items=[
                        dict(
                            type="ACTIVITY",
                            title="Visit the park",
                            description="Explore at a relaxed pace",
                            start_time=start,
                            end_time=end,
                            place_label="P1",
                        )
                    ],
                )
                for day, start, end in [
                    ("2026-12-10", "18:00", "19:00"),
                    ("2026-12-11", "10:00", "12:00"),
                    ("2026-12-12", "10:00", "12:00"),
                ]
            ],
            rationale="Room for breaks",
        )
    )
    return research, schedule
