from typing import List, Dict, Any, Optional
from pydantic import BaseModel, Field


class CriterionItem(BaseModel):
    id: str
    rubricId: Optional[int] = None
    rubricName: Optional[str] = ""
    label: str
    clo: Optional[int] = None
    scope: Optional[str] = ""
    possible: float = 100.0


class ScoreItem(BaseModel):
    lecturerId: int
    lecturerName: str
    teamId: Optional[int] = None
    teamName: Optional[str] = None
    studentId: Optional[int] = None
    studentName: Optional[str] = None
    targetId: str
    targetName: str
    criterionId: str
    earned: float
    status: Optional[str] = "Submitted"
    submittedAt: Optional[str] = None


class CommentItem(BaseModel):
    lecturerName: str
    targetName: str
    rubricName: Optional[str] = None
    commentText: str


class ModerationBriefingRequest(BaseModel):
    assessmentId: int
    assessmentTitle: str
    criteria: List[CriterionItem]
    scores: List[ScoreItem]
    comments: Optional[List[CommentItem]] = Field(default_factory=list)


class CriterionDiscrepancy(BaseModel):
    criterionId: str
    criterionLabel: str
    rubricName: str
    meanPairGapPct: float
    severity: str = Field(description="'CRITICAL', 'MODERATE', or 'LOW'")
    analysis: str


class TargetDiscrepancy(BaseModel):
    targetId: str
    targetName: str
    spreadPct: float
    lecturerScores: Dict[str, float]
    summaryNote: str


class ModerationBriefingResponse(BaseModel):
    assessmentId: int
    assessmentTitle: str
    consensusStatus: str = Field(description="'HIGH_ALIGNMENT', 'MODERATE_DISCREPANCY', or 'ACTION_REQUIRED'")
    meanCohortGapPct: float
    executiveSummary: str
    topDivergentCriteria: List[CriterionDiscrepancy] = Field(default_factory=list)
    priorityReviewTargets: List[TargetDiscrepancy] = Field(default_factory=list)
    moderationActions: List[str] = Field(default_factory=list)

