from langchain_core.language_models.chat_models import BaseChatModel

from app.config import Settings


class LlmConfigError(RuntimeError):
    """The LLM settings are missing or unusable."""


def build_chat_model(settings: Settings) -> BaseChatModel:
    """Create the chat model named by LLM_PROVIDER. Agents depend only on BaseChatModel."""
    provider = (settings.llm_provider or "").strip().lower()
    if not provider:
        raise LlmConfigError("LLM_PROVIDER is not set (use 'openai' or 'anthropic').")
    if not settings.llm_model:
        raise LlmConfigError("LLM_MODEL is not set.")

    if provider == "openai":
        from langchain_openai import ChatOpenAI

        return ChatOpenAI(
            model=settings.llm_model,
            base_url=settings.llm_base_url or None,
            # Local OpenAI-compatible servers ignore the key but the client insists on one.
            api_key=settings.llm_api_key or "not-needed",
            temperature=settings.llm_temperature,
            timeout=settings.llm_timeout_s,
            max_retries=1,
        )

    if provider == "anthropic":
        try:
            from langchain_anthropic import ChatAnthropic
        except ImportError as exc:
            raise LlmConfigError(
                "LLM_PROVIDER=anthropic needs the extra: uv sync --extra anthropic"
            ) from exc
        if not settings.llm_api_key:
            raise LlmConfigError("LLM_API_KEY is required for the anthropic provider.")
        return ChatAnthropic(
            model=settings.llm_model,
            api_key=settings.llm_api_key,
            base_url=settings.llm_base_url or None,
            temperature=settings.llm_temperature,
            timeout=settings.llm_timeout_s,
            max_retries=1,
        )

    raise LlmConfigError(f"Unknown LLM_PROVIDER {provider!r} (use 'openai' or 'anthropic').")
