"""Interactive backend-only planning client: uv run python -m scripts.planning_chat."""

import argparse
import uuid

import httpx


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://localhost:8000")
    parser.add_argument("--session-id", help="Existing planning session UUID")
    parser.add_argument("--session-header", help="Existing X-Session-Id owner token")
    args = parser.parse_args()
    if args.session_id and not args.session_header:
        parser.error("--session-id requires the original --session-header")
    with httpx.Client(
        base_url=args.url,
        timeout=150,
        headers={"X-Session-Id": args.session_header or uuid.uuid4().hex},
    ) as client:
        result = (
            client.get("/v1/planning-sessions/" + args.session_id)
            if args.session_id
            else client.post("/v1/planning-sessions", json={"requestId": str(uuid.uuid4())})
        )
        result.raise_for_status()
        state = result.json()
        path = "/v1/planning-sessions/" + state["sessionId"]
        print("Session:", state["sessionId"])
        print("Commands: :confirm N, :step STEP_ID, :finalize, :delete, :quit")
        step = state["currentStep"]
        while True:
            print("\nStep:", step, "| revision:", state["revision"])
            print(state.get("assistantMessage") or state.get("question", ""))
            proposal = state.get("proposal")
            if proposal:
                for index, option in enumerate(proposal["options"], 1):
                    print(f"  {index}. {option['title']}")
                    print("    ", option["answer"])
            text = input("You: ").strip()
            if text == ":quit":
                print("Session retained; use DELETE to remove conversation data.")
                break
            if text == ":delete":
                client.delete(path).raise_for_status()
                print("Session and turns deleted.")
                break
            if text.startswith(":step "):
                step = text.split(maxsplit=1)[1].upper()
                continue
            if text == ":finalize":
                response = client.post(path + "/finalize", json={"baseRevision": state["revision"]})
                print(response.json())
                continue
            payload = dict(requestId=str(uuid.uuid4()), baseRevision=state["revision"], stepId=step)
            if text.startswith(":confirm "):
                try:
                    index = int(text.split()[1]) - 1
                    if index < 0 or proposal is None:
                        raise ValueError
                    option = proposal["options"][index]
                except (ValueError, IndexError):
                    print("Choose a numbered option from the current proposal.")
                    continue
                payload.update(action="CONFIRM", proposalId=proposal["id"], optionId=option["id"])
            else:
                payload.update(action="CHAT", text=text)
            response = client.post(path + "/turns", json=payload)
            if response.is_error:
                print(response.json())
                refreshed = client.get(path)
                refreshed.raise_for_status()
                state = refreshed.json()
            else:
                state = response.json()
            step = state["currentStep"]


if __name__ == "__main__":
    main()
