"""A scripted chat model for offline runs: replays canned replies, no LLM needed."""

from collections.abc import Callable
from typing import Any

from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.messages import AIMessage, BaseMessage
from langchain_core.outputs import ChatGeneration, ChatResult

Reply = AIMessage | Callable[[list[BaseMessage]], AIMessage]


class ScriptedChatModel(BaseChatModel):
    """Each call returns the next scripted reply; a callable reply sees the conversation."""

    script: list[Any]
    cursor: int = 0

    @property
    def _llm_type(self) -> str:
        return "scripted"

    def bind_tools(self, tools: Any, **kwargs: Any) -> "ScriptedChatModel":
        return self

    def _generate(self, messages: list[BaseMessage], stop=None, run_manager=None, **kwargs):
        if self.cursor >= len(self.script):
            raise RuntimeError("ScriptedChatModel ran out of scripted replies")
        reply = self.script[self.cursor]
        self.cursor += 1
        message = reply(messages) if callable(reply) else reply
        return ChatResult(generations=[ChatGeneration(message=message)])
