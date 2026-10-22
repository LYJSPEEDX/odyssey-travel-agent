"""Send one message to the configured model.

uv run python -m scripts.llm_check
uv run python -m scripts.llm_check "Say hi in French"
"""

import asyncio
import sys

from app.agents.llm import LlmConfigError, build_chat_model
from app.config import get_settings


async def main() -> int:
    s = get_settings()
    try:
        model = build_chat_model(s)
    except LlmConfigError as exc:
        print(f"FAIL config: {exc}")
        return 1
    prompt = sys.argv[1] if len(sys.argv) > 1 else "Reply with the single word: ready"
    print(f"provider={s.llm_provider} model={s.llm_model} base_url={s.llm_base_url or '(default)'}")
    try:
        reply = await model.ainvoke(prompt)
    except Exception as exc:
        print(f"FAIL call: {type(exc).__name__}: {exc}")
        return 1
    print("OK reply:", str(reply.content).strip()[:300])
    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
