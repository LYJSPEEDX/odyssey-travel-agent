"""Live component smoke test. Creates and deletes its own session; spends model/API usage.

Start the shared API, then: uv run python -m scripts.planning_smoke --live
"""

import argparse
import uuid

import httpx

MESSAGES = [
    "I will depart from Sydney, Australia.",
    "My destination is Osaka, Japan.",
    "Fixed dates: depart 2026-12-10 and leave the destination on 2026-12-14.",
    "There are exactly 2 adults and no children, travelling as partners.",
    "Our budget is AUD 5000 for the entire party and whole trip, including flights.",
    "No special requirements or constraints.",
    "A relaxed pace with regular breaks.",
    "I want local food and cultural experiences.",
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://localhost:8000")
    parser.add_argument(
        "--live",
        action="store_true",
        required=True,
        help="Explicitly enable paid model calls and live geocoding",
    )
    args = parser.parse_args()
    with httpx.Client(
        base_url=args.url, timeout=150, headers={"X-Session-Id": "smoke-" + uuid.uuid4().hex}
    ) as client:
        created = client.post("/v1/planning-sessions", json={"requestId": str(uuid.uuid4())})
        created.raise_for_status()
        state = created.json()
        path = "/v1/planning-sessions/" + state["sessionId"]
        try:
            for message in MESSAGES:
                step = state["currentStep"]
                result = client.post(
                    path + "/turns",
                    json=dict(
                        requestId=str(uuid.uuid4()),
                        baseRevision=state["revision"],
                        stepId=step,
                        action="CHAT",
                        text=message,
                    ),
                )
                result.raise_for_status()
                state = result.json()
                print(step, state["outcome"], flush=True)
                if state["outcome"] != "PROPOSE":
                    raise RuntimeError(
                        "Assistant requested clarification: " + state["assistantMessage"]
                    )
                proposal = state["proposal"]
                result = client.post(
                    path + "/turns",
                    json=dict(
                        requestId=str(uuid.uuid4()),
                        baseRevision=state["revision"],
                        stepId=proposal["stepId"],
                        action="CONFIRM",
                        proposalId=proposal["id"],
                        optionId=proposal["options"][0]["id"],
                    ),
                )
                result.raise_for_status()
                state = result.json()
            result = client.post(path + "/finalize", json={"baseRevision": state["revision"]})
            result.raise_for_status()
            request = result.json()["generationRequest"]
            assert request["adults"] == 2 and request["currency"] == "AUD"
            assert request["startDate"] == "2026-12-10"
            print(
                "PASS: confirmed eight steps and exported generationRequest; "
                "no itinerary generated."
            )
        finally:
            client.delete(path).raise_for_status()
            print("Smoke session and turn history deleted.", flush=True)


if __name__ == "__main__":
    main()
