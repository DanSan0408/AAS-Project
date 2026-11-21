"""Telegram alerts from the AI service itself, so a Gemini outage or a crash is reported even when the app cannot tell.

The Spring Boot app watches this service from outside (health every minute). This covers what only the service can see: the
model failing (key, quota, outage), unhandled errors, startup and shutdown. Same bot and chat as the app.

Rules: the first alert of each kind goes out at once; repeats within five minutes are counted and reported afterwards; at most 20
messages per ten minutes. Only the kind of error and the endpoint are sent, never a prompt, a comment, a name or an exception
message (those can hold student text or keys).
"""
import json
import logging
import threading
import time
import urllib.parse
import urllib.request
from typing import Callable, Dict, Optional

from app.config import settings

logger = logging.getLogger("ai_service.alerts")

COALESCE_SECONDS = 300
WINDOW_SECONDS = 600
MAX_PER_WINDOW = 20


def _post_to_telegram(token: str, chat_id: str, text: str) -> bool:
    url = f"https://api.telegram.org/bot{token}/sendMessage"
    data = urllib.parse.urlencode({"chat_id": chat_id, "text": text}).encode("utf-8")
    try:
        with urllib.request.urlopen(urllib.request.Request(url, data=data, method="POST"), timeout=10) as response:
            return response.status == 200
    except Exception as exc:  # the URL holds the bot token, so only the kind of error is logged
        logger.warning("Telegram alert could not be sent (%s)", type(exc).__name__)
        return False


class Alerter:
    def __init__(self, token: str, chat_id: str, label: str, enabled: bool = True,
                 send: Optional[Callable[[str], bool]] = None, clock: Callable[[], float] = time.monotonic,
                 background: bool = True):
        self.token = (token or "").strip()
        self.chat_id = (chat_id or "").strip()
        self.label = label or "production"
        self.enabled = enabled
        self._send = send or (lambda text: _post_to_telegram(self.token, self.chat_id, text))
        self._clock = clock
        self._background = background
        self._lock = threading.Lock()
        self._last_sent: Dict[str, float] = {}
        self._suppressed: Dict[str, int] = {}
        self._sample: Dict[str, str] = {}
        self._sent_times = []
        self._held_back = 0

    @property
    def configured(self) -> bool:
        return self.enabled and bool(self.token) and bool(self.chat_id)

    def alert(self, kind: str, text: str) -> None:
        """Reports a problem of the given kind. Never raises and never blocks the caller."""
        if not self.configured:
            return
        message = None
        with self._lock:
            now = self._clock()
            self._sample[kind] = text
            last = self._last_sent.get(kind)
            due = last is None or now - last >= COALESCE_SECONDS
            if due and self._flood_allows(now):
                message = text
                if self._suppressed.get(kind):
                    message += f"\n+{self._suppressed[kind]} more like this since the last alert"
                if self._held_back:
                    message += f"\n({self._held_back} alert(s) were held back by the flood limit)"
                    self._held_back = 0
                self._suppressed[kind] = 0
                self._last_sent[kind] = now
            else:
                self._suppressed[kind] = self._suppressed.get(kind, 0) + 1
                if due:
                    self._held_back += 1
                if self._suppressed[kind] == 1 and self._background and last is not None:
                    self._schedule_flush(max(1.0, COALESCE_SECONDS - (now - last)))
        if message:
            self._dispatch(message)

    def flush(self) -> None:
        """Reports repeats that were counted but never followed by another alert of their kind."""
        messages = []
        with self._lock:
            now = self._clock()
            for kind, count in list(self._suppressed.items()):
                if count > 0 and now - self._last_sent.get(kind, 0) >= COALESCE_SECONDS and self._flood_allows(now):
                    messages.append(f"{self._sample.get(kind, kind)}\n+{count} more like this since the last alert")
                    self._suppressed[kind] = 0
                    self._last_sent[kind] = now
        for message in messages:
            self._dispatch(message)

    def _flood_allows(self, now: float) -> bool:
        self._sent_times = [t for t in self._sent_times if now - t < WINDOW_SECONDS]
        if len(self._sent_times) >= MAX_PER_WINDOW:
            return False
        self._sent_times.append(now)
        return True

    def _schedule_flush(self, delay: float) -> None:
        timer = threading.Timer(delay + 1, self.flush)
        timer.daemon = True
        timer.start()

    def _dispatch(self, text: str) -> None:
        full = f"[{self.label}] [ai-service] {text}"[:3500]
        if self._background:
            threading.Thread(target=self._safe_send, args=(full,), daemon=True).start()
        else:
            self._safe_send(full)

    def _safe_send(self, text: str) -> None:
        try:
            self._send(text)
        except Exception as exc:
            logger.warning("Telegram alert failed (%s)", type(exc).__name__)


def _chat_id() -> str:
    return settings.telegram_alert_chat_id or settings.telegram_deploy_chat_id


alerter = Alerter(settings.telegram_bot_token, _chat_id(), settings.adproject_environment_label, settings.ai_alerts_enabled)


def alert(kind: str, text: str) -> None:
    alerter.alert(kind, text)
