from typing import List, Dict, Any, Optional
from pydantic import BaseModel, Field


class PeerRatingItem(BaseModel):
    evaluatorId: int
    evaluatorName: str
    evaluatedId: int
    evaluatedName: str
    criterionLabel: Optional[str] = "General"
    earned: float
    possible: float = 100.0


class PeerCommentItem(BaseModel):
    evaluatorName: str
    evaluatedName: str
    question: Optional[str] = ""
    commentText: str


class MemberOverview(BaseModel):
    studentId: int
    studentName: str
    email: Optional[str] = ""


class TeamDynamicsRequest(BaseModel):
    groupId: int
    groupName: str
    assessmentId: int
    assessmentTitle: str
    members: List[MemberOverview]
    peerRatings: List[PeerRatingItem] = Field(default_factory=list)
    peerComments: List[PeerCommentItem] = Field(default_factory=list)


class MemberMetric(BaseModel):
    studentId: int
    studentName: str
    selfScoreAvg: Optional[float] = None
    peerScoreAvg: float
    selfInflationGap: Optional[float] = None
    status: str = Field(description="'CONTRIBUTING', 'SUSPECTED_FREE_RIDER', 'OVER_CONFIDENT', 'UNDER_EVALUATED'")
    contributionSummary: str


class AnomalyFlag(BaseModel):
    type: str = Field(description="'FREE_RIDER', 'COLLUSION', 'SENTIMENT_MARK_MISMATCH', 'SELF_INFLATION'")
    severity: str = Field(description="'HIGH', 'MEDIUM', 'LOW'")
    targetStudent: Optional[str] = None
    description: str


class TeamDynamicsResponse(BaseModel):
    groupId: int
    groupName: str
    teamHealthScore: int = Field(ge=0, le=100, description="Overall team collaboration health (0-100)")
    collaborationStatus: str = Field(description="'HEALTHY_COLLABORATION', 'SUSPECTED_COLLUSION', 'FREE_RIDER_DETECTED', 'HIGH_FRICTION'")
    executiveSummary: str
    memberMetrics: List[MemberMetric] = Field(default_factory=list)
    anomalyFlags: List[AnomalyFlag] = Field(default_factory=list)
    recommendedActions: List[str] = Field(default_factory=list)

