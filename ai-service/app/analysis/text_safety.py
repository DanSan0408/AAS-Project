"""Helpers for handling untrusted text and unreliable model output.

Student comments are untrusted: they can contain control characters, markup meant to break out of a prompt, or
instructions aimed at the model. Model replies are unreliable: they may be wrapped in prose, truncated, or the wrong shape.
Nothing here calls a model, so it is all easy to test.
"""
import json
import re
import unicodedata
from typing import Any, Dict, Iterable, List, Optional

MAX_COMMENT_CHARS = 500
MAX_COMMENTS = 80

_ZERO_WIDTH = dict.fromkeys(map(ord, "\u200b\u200c\u200d\u2060\ufeff\u00ad"), None)


def clean_text(value: Any, max_len: int = MAX_COMMENT_CHARS) -> str:
    """Plain, bounded text: no control or invisible characters, no angle brackets, collapsed whitespace."""
    if value is None:
        return ""
    text = str(value).translate(_ZERO_WIDTH)
    text = "".join(ch for ch in text if ch in "\n\t " or unicodedata.category(ch)[0] != "C")
    text = text.replace("<", "(").replace(">", ")")   # a comment must not be able to close or open our delimiters
    text = re.sub(r"\s+", " ", text).strip()
    return text[:max_len]


def model_text(content: Any) -> str:
    """The text of a model reply, whether the library hands back a string or a list of content blocks."""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts: List[str] = []
        for block in content:
            if isinstance(block, str):
                parts.append(block)
            elif isinstance(block, dict) and isinstance(block.get("text"), str):
                parts.append(block["text"])
        return "".join(parts)
    return "" if content is None else str(content)


def extract_json(content: Any) -> Dict[str, Any]:
    """The first complete JSON object in a model reply. Raises ValueError when there is none."""
    text = model_text(content)
    decoder = json.JSONDecoder()
    start = text.find("{")
    while start != -1:
        try:
            value, _ = decoder.raw_decode(text[start:])
            if isinstance(value, dict):
                return value
        except ValueError:
            pass
        start = text.find("{", start + 1)
    raise ValueError("The reply did not contain a JSON object.")


def one_of(value: Any, allowed: Iterable[str], default: str) -> str:
    """The value if it is one of the allowed labels (any case), otherwise the default."""
    wanted = str(value).strip().upper() if value is not None else ""
    for option in allowed:
        if option.upper() == wanted:
            return option
    return default


def text_field(value: Any, max_len: int) -> str:
    return clean_text(value, max_len)


def string_list(value: Any, max_items: int, max_len: int) -> List[str]:
    if not isinstance(value, list):
        return []
    items = [clean_text(v, max_len) for v in value if isinstance(v, str)]
    return [i for i in items if i][:max_items]


class Pseudonyms:
    """Stable labels ("Student 1", "Assessor A") for real names, so the model never sees who anyone is."""

    def __init__(self) -> None:
        self._by_key: Dict[str, str] = {}
        self._real: Dict[str, str] = {}
        self._counts: Dict[str, int] = {}

    def label(self, kind: str, key: Any, real_name: Optional[str]) -> str:
        composite = f"{kind}:{key}"
        if composite not in self._by_key:
            self._counts[kind] = self._counts.get(kind, 0) + 1
            number = self._counts[kind]
            label = f"{kind} {number}"
            self._by_key[composite] = label
            self._real[label] = real_name or str(key)
        return self._by_key[composite]

    def restore(self, text: str) -> str:
        """Puts real names back into text the model wrote. Longer labels first so "Student 10" is not read as "Student 1"."""
        for label in sorted(self._real, key=len, reverse=True):
            text = re.sub(rf"\b{re.escape(label)}\b", self._real[label].replace("\\", "\\\\"), text)
        return text
