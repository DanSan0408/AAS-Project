import logging
import os
import re
import time
from typing import Any, Dict, Optional

import httpx
from langchain_core.messages import HumanMessage, SystemMessage
from langchain_google_genai import ChatGoogleGenerativeAI

from app import alerts, usage
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


def _typesafe_api_key() -> str:
    return (settings.typesafe_api_key or os.environ.get("TYPESAFE_API_KEY", "")).strip()


def typesafe_available() -> bool:
    """True when TypeSafe AI (Jev) is configured and not explicitly disabled."""
    flag = (settings.typesafe_enabled or "auto").strip().lower()
    if flag in ("false", "off", "0", "no"):
        return False
    return bool(_typesafe_api_key())


def invoke_system_one(state: Any, questions: Dict[str, Any], timeout: int = 30) -> Dict[str, Any]:
    """Calls TypeSafe AI's System One endpoint (/v1/systemone) for structured classification/gating without prose generation.

    Returns the `answers` dictionary from the API response. Raises AiUnavailable on failure; never logs state or keys.
    """
    api_key = _typesafe_api_key()
    if not api_key:
        raise AiUnavailable("TYPESAFE_API_KEY is not set.")
    model = (settings.typesafe_model or "jev-latest").strip() or "jev-latest"
    url = settings.typesafe_base_url.rstrip("/") + "/v1/systemone"
    headers = {"Authorization": "Bearer " + api_key, "Content-Type": "application/json"}
    payload = {"model": model, "state": state, "questions": questions}
    call = usage.start_call("typesafe", model)
    try:
        for attempt in (1, 2):
            usage.note_attempt()
            try:
                response = httpx.post(url, headers=headers, json=payload, timeout=timeout)
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
            body = response.json()
            if not isinstance(body, dict) or not isinstance(body.get("answers"), dict):
                raise ValueError("Missing answers in TypeSafe response.")
            if isinstance(body.get("model"), str) and body["model"].strip():
                call.model = body["model"].strip()
            usage.add_typesafe_usage(body.get("usage"))
            usage.finish_call(call, ok=True)
            return body["answers"]
        raise ValueError("No reply.")
    except Exception as exc:
        kind = f"HTTP{exc.status}" if isinstance(exc, ProviderHttpError) else type(exc).__name__
        usage.finish_call(call, ok=False, error=kind)
        logger.warning("TypeSafe AI call failed (%s)", kind)
        raise AiUnavailable("TypeSafe AI did not return a usable answer.") from exc


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
        usage.note_attempt()
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
        body = response.json()
        usage.add_deepseek_usage(body.get("usage") if isinstance(body, dict) else None)   # billed even when the reply is blank
        content = ((body.get("choices") or [{}])[0].get("message") or {}).get("content") or ""
        if content.strip():
            return content
        if attempt == 2:
            raise ValueError("The reply was blank.")
        time.sleep(1)
    raise ValueError("No reply.")


def _gemini_json_text(system: str, user: str, temperature: float):
    usage.note_attempt()
    message = get_gemini_llm(temperature).invoke([SystemMessage(content=system), HumanMessage(content=user)])
    usage.add_langchain_usage(getattr(message, "usage_metadata", None))
    return message.content


def invoke_json(system: str, user: str, temperature: float = 0.1) -> dict:
    """Asks the model and returns the JSON object in its reply.

    Raises AiUnavailable on any failure (no key, network, quota, a reply that is not JSON). Only the kind of error is logged
    (for an HTTP error, its status code): never the prompt, which contains student comments, and never the key.
    """
    provider = active_provider()
    call = usage.start_call(provider, settings.deepseek_model if provider == "deepseek" else settings.gemini_model)
    try:
        if provider == "deepseek":
            result = extract_json(_deepseek_json_text(system, user, temperature))
        else:
            result = extract_json(_gemini_json_text(system, user, temperature))
        usage.finish_call(call, ok=True)
        return result
    except Exception as exc:  # deliberately broad: whatever went wrong, the caller falls back to calculated results
        kind = f"HTTP{exc.status}" if isinstance(exc, ProviderHttpError) else type(exc).__name__
        usage.finish_call(call, ok=False, error=kind)
        logger.warning("AI call failed (%s: %s)", provider, kind)
        label = f"HTTP {exc.status}" if isinstance(exc, ProviderHttpError) else kind
        alerts.alert(f"{provider}:{kind}",
                     f"[ERROR] The {provider} call failed ({label}). AI features are falling back to calculated results.")
        raise AiUnavailable("The AI did not return a usable answer.") from exc


def provider_balance() -> dict:
    """What is left on the account, where the provider says so (DeepSeek does; Gemini has no such call).

    Always returns a plain dict and never raises: {"provider", "supported", "available", "balances": [{currency, total, granted,
    toppedUp}], "error"}. Only the kind of failure is reported, never a body or the key.
    """
    provider = active_provider()
    if provider != "deepseek":
        return {"provider": provider, "supported": False, "available": None, "balances": [], "error": None}
    api_key = settings.deepseek_api_key or os.environ.get("DEEPSEEK_API_KEY", "")
    if not api_key:
        return {"provider": provider, "supported": True, "available": None, "balances": [], "error": "no API key"}
    try:
        response = httpx.get(settings.deepseek_base_url.rstrip("/") + "/user/balance",
                             headers={"Authorization": "Bearer " + api_key, "Accept": "application/json"}, timeout=15)
        if response.status_code != 200:
            return {"provider": provider, "supported": True, "available": None, "balances": [], "error": f"HTTP {response.status_code}"}
        body = response.json()
        infos = body.get("balance_infos") if isinstance(body, dict) else None
        balances = []
        for info in infos if isinstance(infos, list) else []:
            if isinstance(info, dict):
                balances.append({"currency": str(info.get("currency") or "")[:8], "total": _amount(info.get("total_balance")),
                                 "granted": _amount(info.get("granted_balance")), "toppedUp": _amount(info.get("topped_up_balance"))})
        available = body.get("is_available") if isinstance(body, dict) and isinstance(body.get("is_available"), bool) else None
        return {"provider": provider, "supported": True, "available": available, "balances": balances, "error": None}
    except Exception as exc:
        return {"provider": provider, "supported": True, "available": None, "balances": [], "error": type(exc).__name__}


def _amount(value) -> Optional[float]:
    """The provider sends amounts as strings ("110.00")."""
    try:
        number = float(value)
        return number if number == number and abs(number) != float("inf") else None
    except (TypeError, ValueError):
        return None
