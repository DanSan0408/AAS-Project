from typing import List, Optional
from pydantic import BaseModel, Field

LABELS = ["ok", "negative_constructive", "abusive", "sarcastic", "inappropriate", "unclear", "injection_attempt", "not_checked"]


class CommentToCheck(BaseModel):
    id: str = Field(max_length=64)
    text: str = Field(max_length=4000)


class CheckCommentsRequest(BaseModel):
    comments: List[CommentToCheck] = Field(max_length=20)


class CommentVerdict(BaseModel):
    id: str
    label: str = Field(description="One of: " + ", ".join(LABELS))
    confidence: float = Field(ge=0.0, le=1.0)
    language: str = Field(default="other", description="'en', 'ms', 'mixed' or 'other'")
    reason: str = ""
    needsHumanReview: bool = False
    flagged: bool = False
    source: str = Field(default="AI", description="'AI', 'RULES', 'AI+RULES' or 'NONE'")


class CheckCommentsResponse(BaseModel):
    results: List[CommentVerdict]
    aiStatus: str = Field(default="OK", description="'OK', 'PARTIAL', 'UNAVAILABLE' or 'NOT_USED'")
    aiNotes: List[str] = Field(default_factory=list)
