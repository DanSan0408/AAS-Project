"""What each AI request used: model calls, tokens, time, failures and an estimated cost.

Only numbers and the kind of error are recorded, never a prompt, a comment, a name or an exception message (those can hold
student text or keys). The app saves the summary this module produces (it arrives in the X-AI-Usage response header) and shows it
to the Super Admin, so a request that makes no model call (for example nothing to read) sends no header at all.

Cost is an ESTIMATE from the published price list: the provider's own invoice is the truth, and the DeepSeek balance shown next
to it is the real figure. Prices are per one million tokens. DeepSeek charges double at peak (01:00-04:00 and 06:00-10:00 UTC,
Monday to Friday); Chinese public holidays are off-peak there, but a calendar of them is not kept here, so a holiday is counted at the
peak rate and the estimate errs on the high side.
"""
import contextlib
import contextvars
import json
import logging
import threading
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Dict, Iterator, List, Optional, Tuple

from app.config import settings

logger = logging.getLogger("ai_service.usage")

MAX_ERROR_KINDS = 10

# Per one million tokens, as (off-peak, peak), from https://api-docs.deepseek.com/quick_start/pricing. A model that is not listed
# here is "not priced": its tokens are still counted, its cost is left empty. AI_PRICES_JSON adds or replaces entries.
_DEEPSEEK_FLASH = {"hit": (0.003, 0.006), "miss": (0.15, 0.3), "out": (0.6, 1.2)}
DEFAULT_PRICES: Dict[str, Dict[str, Tuple[float, float]]] = {
    "deepseek-flash": _DEEPSEEK_FLASH,
    "deepseek-v4-flash": _DEEPSEEK_FLASH,           # the old name; the provider now serves it with deepseek-flash
    "deepseek-v4-pro": {"hit": (0.022, 0.044), "miss": (0.66, 1.32), "out": (1.98, 3.96)},
}

_price_cache: Optional[Dict[str, Dict[str, Tuple[float, float]]]] = None
_price_source: Optional[str] = None


def _pair(value) -> Optional[Tuple[float, float]]:
    """A price as given in AI_PRICES_JSON: one number (same at all hours) or [off-peak, peak]."""
    if isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        return (float(value), float(value)) if value >= 0 else None
    if isinstance(value, (list, tuple)) and len(value) == 2 and all(isinstance(v, (int, float)) and not isinstance(v, bool) and v >= 0 for v in value):
        return float(value[0]), float(value[1])
    return None


def prices() -> Dict[str, Dict[str, Tuple[float, float]]]:
    """The price table: the built-in one with AI_PRICES_JSON applied on top. A bad override is ignored and logged once."""
    global _price_cache, _price_source
    raw = (settings.ai_prices_json or "").strip()
    if _price_cache is not None and _price_source == raw:
        return _price_cache
    table = {model: dict(entry) for model, entry in DEFAULT_PRICES.items()}
    if raw:
        try:
            loaded = json.loads(raw)
            if not isinstance(loaded, dict):
                raise ValueError("not an object")
            for model, entry in loaded.items():
                parsed = {key: _pair(entry.get(key)) for key in ("hit", "miss", "out")} if isinstance(entry, dict) else {}
                if isinstance(model, str) and model and all(parsed.values()) and len(parsed) == 3:
                    table[model.strip().lower()] = parsed
                else:
                    logger.warning("AI_PRICES_JSON entry ignored (each model needs hit, miss and out prices)")
        except (ValueError, TypeError):
            logger.warning("AI_PRICES_JSON is not valid JSON; the built-in prices are used")
    _price_cache, _price_source = table, raw
    return table


def is_peak(moment: datetime) -> bool:
    moment = moment.astimezone(timezone.utc)
    return moment.weekday() < 5 and (1 <= moment.hour < 4 or 6 <= moment.hour < 10)


def estimate_cost(model: str, prompt_tokens: int, cache_hit_tokens: int, completion_tokens: int,
                  moment: Optional[datetime] = None) -> Optional[float]:
    """US dollars for one call, or None when the model has no price."""
    entry = prices().get((model or "").strip().lower())
    if entry is None:
        return None
    slot = 1 if is_peak(moment or datetime.now(timezone.utc)) else 0
    hit = max(min(cache_hit_tokens, prompt_tokens), 0)
    miss = max(prompt_tokens - hit, 0)
    return (hit * entry["hit"][slot] + miss * entry["miss"][slot] + max(completion_tokens, 0) * entry["out"][slot]) / 1_000_000


@dataclass
class CallUsage:
    """One call to the model, as seen from invoke_json: all its attempts together."""
    provider: str
    model: str
    started: float = field(default_factory=time.monotonic)
    attempts: int = 0
    prompt_tokens: int = 0
    cache_hit_tokens: int = 0
    completion_tokens: int = 0
    reasoning_tokens: int = 0


@dataclass
class RequestUsage:
    """Everything the model calls of one request used. Thread-safe: a graph may call the model from several threads."""
    calls: int = 0
    failed_calls: int = 0
    retries: int = 0
    prompt_tokens: int = 0
    cache_hit_tokens: int = 0
    completion_tokens: int = 0
    reasoning_tokens: int = 0
    model_ms: int = 0
    cost_usd: float = 0.0
    unpriced_calls: int = 0
    provider: str = ""
    model: str = ""
    errors: List[str] = field(default_factory=list)
    _lock: threading.Lock = field(default_factory=threading.Lock, repr=False)

    def add(self, call: CallUsage, ok: bool, error: Optional[str], now: Optional[datetime] = None) -> None:
        cost = estimate_cost(call.model, call.prompt_tokens, call.cache_hit_tokens, call.completion_tokens, now)
        with self._lock:
            self.calls += 1
            self.failed_calls += 0 if ok else 1
            self.retries += max(call.attempts - 1, 0)
            self.prompt_tokens += call.prompt_tokens
            self.cache_hit_tokens += call.cache_hit_tokens
            self.completion_tokens += call.completion_tokens
            self.reasoning_tokens += call.reasoning_tokens
            self.model_ms += int((time.monotonic() - call.started) * 1000)
            if cost is None:
                self.unpriced_calls += 1
            else:
                self.cost_usd += cost
            self.provider = call.provider
            self.model = call.model
            if error and len(self.errors) < MAX_ERROR_KINDS and error not in self.errors:
                self.errors.append(error)

    def header_value(self) -> Optional[str]:
        """The summary the app stores, as compact ASCII JSON; None when the request made no model call."""
        with self._lock:
            if self.calls == 0:
                return None
            return json.dumps({
                "provider": self.provider, "model": self.model, "calls": self.calls, "failedCalls": self.failed_calls,
                "retries": self.retries, "promptTokens": self.prompt_tokens, "cachedPromptTokens": self.cache_hit_tokens,
                "completionTokens": self.completion_tokens, "reasoningTokens": self.reasoning_tokens,
                "modelMs": self.model_ms,
                "costUsd": None if self.unpriced_calls else round(self.cost_usd, 8),
                "errors": self.errors,
            }, separators=(",", ":"))


_request: contextvars.ContextVar[Optional[RequestUsage]] = contextvars.ContextVar("ai_request_usage", default=None)
_call: contextvars.ContextVar[Optional[CallUsage]] = contextvars.ContextVar("ai_call_usage", default=None)


@contextlib.contextmanager
def track() -> Iterator[RequestUsage]:
    """Collects the usage of every model call made inside the block (one HTTP request to this service)."""
    usage = RequestUsage()
    token = _request.set(usage)
    try:
        yield usage
    finally:
        _request.reset(token)


def start_call(provider: str, model: str) -> CallUsage:
    call = CallUsage(provider=provider, model=model)
    _call.set(call)
    return call


def note_attempt() -> None:
    call = _call.get()
    if call is not None:
        call.attempts += 1


def _count(value) -> int:
    return value if isinstance(value, int) and not isinstance(value, bool) and value >= 0 else 0


def add_tokens(prompt: int = 0, cache_hit: int = 0, completion: int = 0, reasoning: int = 0) -> None:
    """Adds what the provider reported for one attempt (a blank reply that is retried is still billed)."""
    call = _call.get()
    if call is not None:
        call.prompt_tokens += _count(prompt)
        call.cache_hit_tokens += _count(cache_hit)
        call.completion_tokens += _count(completion)
        call.reasoning_tokens += _count(reasoning)


def add_deepseek_usage(usage) -> None:
    """Reads the `usage` object of a chat completion. The cache counts are documented in two places, so both are read."""
    if not isinstance(usage, dict):
        return
    details = usage.get("prompt_tokens_details") if isinstance(usage.get("prompt_tokens_details"), dict) else {}
    out_details = usage.get("completion_tokens_details") if isinstance(usage.get("completion_tokens_details"), dict) else {}
    cache_hit = _count(usage.get("prompt_cache_hit_tokens")) or _count(details.get("prompt_cache_hit_tokens")) or _count(details.get("cached_tokens"))
    add_tokens(prompt=usage.get("prompt_tokens"), cache_hit=cache_hit, completion=usage.get("completion_tokens"),
               reasoning=out_details.get("reasoning_tokens"))


def add_langchain_usage(metadata) -> None:
    """Reads LangChain's usage_metadata of a Gemini reply (input and output counts, cached and reasoning parts when present)."""
    if not isinstance(metadata, dict):
        return
    in_details = metadata.get("input_token_details") if isinstance(metadata.get("input_token_details"), dict) else {}
    out_details = metadata.get("output_token_details") if isinstance(metadata.get("output_token_details"), dict) else {}
    add_tokens(prompt=metadata.get("input_tokens"), cache_hit=in_details.get("cache_read"),
               completion=metadata.get("output_tokens"), reasoning=out_details.get("reasoning"))


def finish_call(call: CallUsage, ok: bool, error: Optional[str] = None) -> None:
    request = _request.get()
    if request is not None:
        request.add(call, ok, error)
    _call.set(None)
