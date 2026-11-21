"""A word-list pass over comments: cheap, predictable, and always available.

It is deliberately cautious. It escalates (injection attempts, clear profanity, discriminatory generalisations) and it asks for a
human look at milder or quoted language, but it never calls a comment "fine": only the model, reading the whole comment,
can say that, and a rule hit alone is never a reason to reject. Blunt but fair criticism ("missed three meetings") has no words
here on purpose.
"""
import re
from dataclasses import dataclass
from typing import List, Optional

STRONG = [
    # English
    "fuck", "fucking", "shit", "bitch", "asshole", "bastard", "dickhead", "motherfucker", "retard", "cunt",
    # Bahasa Melayu
    "bodoh", "bangang", "sial", "celaka", "babi", "anjing", "pukimak", "puki", "pantat", "mampus", "haramjadah", "kimak", "lancau",
]
MILD = ["idiot", "stupid", "moron", "dumb", "useless", "pemalas", "tak guna", "tidak guna", "sampah", "loser"]

_GROUP = r"(malays?|melayu|chinese|cina|indians?|india|muslims?|hindus?|christians?|kristian|buddhist|orang asli)"
_STEREOTYPE = r"(same|sama|lazy|malas|stupid|bodoh|dirty|kotor|cheats?|penipu|dumb)"
DISCRIMINATION = [
    re.compile(rf"\b(all|semua|every)\s+{_GROUP}\b.{{0,30}}\b{_STEREOTYPE}\b", re.I),
    re.compile(rf"\b{_GROUP}\s+(babi|bodoh|sial|bangang|dirty|kotor|stupid)\b", re.I),
    re.compile(r"\bkeling\b", re.I),
]

INJECTION = [
    re.compile(r"ignore\s+(all\s+|the\s+|any\s+)?(previous|prior|above|earlier)\s+(instructions?|prompts?|rules?|text)", re.I),
    re.compile(r"(disregard|forget)\s+(all\s+|the\s+)?(previous|prior|above)", re.I),
    re.compile(r"\bsystem\s+prompt\b", re.I),
    re.compile(r"\byou\s+are\s+now\b", re.I),
    re.compile(r"(approve|accept)\s+(this|all|every)\s+(comment|comments|mark|marks)", re.I),
    re.compile(r"mark\s+everyone\s+as", re.I),
    re.compile(r"abaikan\s+(semua\s+)?(arahan|tugas)", re.I),
    re.compile(r"(output|respond|reply)\s+(only\s+)?(with\s+)?(json|\{)", re.I),
    re.compile(r'"label"\s*:', re.I),
]

_OBFUSCATION = {"a": "a@4", "e": "e3", "i": "i1!", "o": "o0", "u": "u*#v", "s": "s$5", "c": "c(", "k": "k"}
_QUOTES = re.compile(r"""("[^"]{0,200}"|“[^”]{0,200}”|'[^']{0,200}')""")


def _fuzzy(word: str) -> re.Pattern:
    """The word, tolerating repeated letters, symbol substitutions and separators (f u c k, sh1t, f*ck, fuuuck)."""
    parts = []
    for ch in word:
        classes = _OBFUSCATION.get(ch, ch)
        if ch not in _OBFUSCATION:
            classes = ch
        if ch in "aeiou":
            classes += "*#@"      # f*ck, sh*t
        parts.append(rf"[{re.escape(classes)}]+")
    return re.compile(r"(?<![a-z0-9])" + r"[\W_]{0,2}".join(parts) + r"(?![a-z0-9])", re.I)


_STRONG_RE = [(w, _fuzzy(w)) for w in STRONG]
# Masked spellings such as f***, f**k, sh*t, b*tch.
_STRONG_RE += [("masked", re.compile(p, re.I)) for p in (
    r"(?<![a-z0-9])f[\*#@]{2,}(?:k|king|ker|ed)?(?![a-z0-9])", r"(?<![a-z0-9])sh[\*#@]+t(?![a-z0-9])", r"(?<![a-z0-9])b[\*#@]+tch(?![a-z0-9])")]
_MILD_RE = [(w, re.compile(rf"(?<![a-z]){re.escape(w)}(?![a-z])", re.I)) for w in MILD]


@dataclass
class RuleVerdict:
    label: str
    confidence: float
    reason: str
    kind: str          # injection | discrimination | strong | strong_quoted | mild


def _inside_quotes(text: str, start: int, end: int) -> bool:
    return any(m.start() <= start and end <= m.end() for m in _QUOTES.finditer(text))


def check(text: str) -> Optional[RuleVerdict]:
    """The most serious thing the word list finds in the text, or None."""
    if not text:
        return None
    for pattern in INJECTION:
        if pattern.search(text):
            return RuleVerdict("injection_attempt", 0.95, "The comment tries to give instructions to the review system.", "injection")
    for pattern in DISCRIMINATION:
        if pattern.search(text):
            return RuleVerdict("abusive", 0.85, "Possible discriminatory generalisation about a group of people.", "discrimination")
    strong_hits = []
    for word, pattern in _STRONG_RE:
        for m in pattern.finditer(text):
            strong_hits.append(_inside_quotes(text, m.start(), m.end()))
    if strong_hits:
        if all(strong_hits):
            return RuleVerdict("unclear", 0.5, "Contains profanity inside quotation marks, which may be a report of what someone said.", "strong_quoted")
        return RuleVerdict("abusive", 0.8, "Contains profanity or an insult.", "strong")
    for word, pattern in _MILD_RE:
        if pattern.search(text):
            return RuleVerdict("unclear", 0.45, "Contains a word that is sometimes used as an insult.", "mild")
    return None


def hits_for(texts: List[str]) -> List[Optional[RuleVerdict]]:
    return [check(t) for t in texts]
