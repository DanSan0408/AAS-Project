import logging
import os
import re
import time

import httpx
from langchain_core.messages import HumanMessage, SystemMessage
from langchain_google_genai import ChatGoogleGenerativeAI

from app import alerts
from app.analysis.text_safety import extract_json
from app.config import settings

logger = logging.getLogger("ai_service.llm")


class AiUnavailable(Exception):
    """The model could not give a usable answer. The message is safe to show to a user."""


def get_gemini_llm(temperature: float = 0.2) -> ChatGoogleGenerativeAI:
    """Returns a ChatGoogleGenerativeAI instance using the configured GEMINI_API_KEY and model."""
    api_key = settings.gemini_api_key or os.environ.get("GEMINI_API_KEY", "")
    if not api_key:
        raise ValueError("GEMINI_API_KEY is not set. Please add it to your .env or environment variables.")

    options = dict(
        model=settings.gemini_model,
        google_api_key=api_key,
        timeout=60,
        max_retries=2,
        # Room for the answer after the model has thought: thinking tokens count against this limit.
        max_output_tokens=8192,
    )
    if _uses_sampling_settings(settings.gemini_model):
        options["temperature"] = temperature
    elif settings.gemini_thinking_level:
        options["thinking_level"] = settings.gemini_thinking_level
    return ChatGoogleGenerativeAI(**options)


def _uses_sampling_settings(model: str) -> bool:
    """Gemini 3 and later ignore temperature and will reject it; earlier models still use it."""
    match = re.match(r"gemini-(\d+)", model or "")
    return not (match and int(match.group(1)) >= 3)


def active_provider() -> str:
    """"deepseek" or "gemini". Auto picks DeepSeek when it has a key, so commenting out the Gemini variables is enough to switch."""
    choice = (settings.ai_provider or "auto").strip().lower()
    if choice in ("deepseek", "gemini"):
        return choice
    return "deepseek" if (settings.deepseek_api_key or os.environ.get("DEEPSEEK_API_KEY", "")) else "gemini"


class ProviderHttpError(Exception):
    """The provider answered with an error status. Only the status is kept: the body can echo the request."""

    def __init__(self, status: int):
        super().__init__(f"HTTP {status}")
        self.status = status


_RETRY_STATUSES = {429, 500, 502, 503, 504}


def _deepseek_json_text(system: str, user: str, temperature: float) -> str:
    """One chat completion in JSON mode; returns the reply text. Retries once on a blank reply (JSON mode can return spaces) or a busy server."""
    api_key = settings.deepseek_api_key or os.environ.get("DEEPSEEK_API_KEY", "")
    if not api_key:
        raise ValueError("DEEPSEEK_API_KEY is not set. Please add it to your .env or environment variables.")
    if "json" not in (system + user).lower():
        system += "\n\nReply with a single JSON object."   # JSON mode refuses a prompt that never says "json"
    thinking = settings.deepseek_thinking.strip().lower() == "enabled"
    payload = {
        "model": settings.deepseek_model,
        "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}],
        "response_format": {"type": "json_object"},
        "thinking": {"type": "enabled" if thinking else "disabled"},
        "max_tokens": 4096,
        "stream": False,
    }
    if not thinking:
        payload["temperature"] = temperature   # thinking mode ignores it
    url = settings.deepseek_base_url.rstrip("/") + "/chat/completions"
    headers = {"Authorization": "Bearer " + api_key, "Content-Type": "application/json"}
    for attempt in (1, 2):
        try:
            response = httpx.post(url, headers=headers, json=payload, timeout=60)
        except httpx.TransportError:
            if attempt == 2:
                raise
            time.sleep(1)
            continue
        if response.status_code in _RETRY_STATUSES and attempt == 1:
            time.sleep(1)
            continue
        if response.status_code != 200:
            raise ProviderHttpError(response.status_code)
        content = ((response.json().get("choices") or [{}])[0].get("message") or {}).get("content") or ""
        if content.strip():
            return content
        if attempt == 2:
            raise ValueError("The reply was blank.")
        time.sleep(1)
    raise ValueError("No reply.")


def _gemini_json_text(system: str, user: str, temperature: float):
    return get_gemini_llm(temperature).invoke([SystemMessage(content=system), HumanMessage(content=user)]).content


def invoke_json(system: str, user: str, temperature: float = 0.1) -> dict:
    """Asks the model and returns the JSON object in its reply.

    Raises AiUnavailable on any failure (no key, network, quota, a reply that is not JSON). Only the kind of error is logged
    (for an HTTP error, its status code): never the prompt, which contains student comments, and never the key.
    """
    provider = active_provider()
    try:
        if provider == "deepseek":
            return extract_json(_deepseek_json_text(system, user, temperature))
        return extract_json(_gemini_json_text(system, user, temperature))
    except Exception as exc:  # deliberately broad: whatever went wrong, the caller falls back to calculated results
        kind = f"HTTP{exc.status}" if isinstance(exc, ProviderHttpError) else type(exc).__name__
        logger.warning("AI call failed (%s: %s)", provider, kind)
        label = f"HTTP {exc.status}" if isinstance(exc, ProviderHttpError) else kind
        alerts.alert(f"{provider}:{kind}",
                     f"[ERROR] The {provider} call failed ({label}). AI features are falling back to calculated results.")
        raise AiUnavailable("The AI did not return a usable answer.") from exc
