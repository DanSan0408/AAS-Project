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
    members: List[MemberOverview] = Field(max_length=60)
    peerRatings: List[PeerRatingItem] = Field(default_factory=list, max_length=6000)
    peerComments: List[PeerCommentItem] = Field(default_factory=list, max_length=600)


class MemberMetric(BaseModel):
    studentId: int
    studentName: str
    selfScoreAvg: Optional[float] = None
    peerScoreAvg: Optional[float] = Field(default=None, description="None when nobody has rated this student")
    peerRatingCount: int = 0
    peerRaterCount: int = 0
    selfInflationGap: Optional[float] = None
    status: str = Field(description="'CONTRIBUTING', 'SUSPECTED_FREE_RIDER', 'OVER_CONFIDENT', 'UNDER_EVALUATED'")
    contributionSummary: str


class AnomalyFlag(BaseModel):
    type: str = Field(description="'FREE_RIDER', 'COLLUSION', 'SENTIMENT_MARK_MISMATCH', 'SELF_INFLATION'")
    severity: str = Field(description="'HIGH', 'MEDIUM', 'LOW'")
    targetStudent: Optional[str] = None
    description: str
    evidence: str = Field(default="", description="The numbers behind the flag, so a supervisor can judge it")
    source: str = Field(default="CALCULATED", description="'CALCULATED' (from the ratings) or 'AI' (from the written comments)")


class TeamDynamicsResponse(BaseModel):
    groupId: int
    groupName: str
    teamHealthScore: Optional[int] = Field(default=None, ge=0, le=100, description="Calculated from the ratings; None when there is not enough peer data")
    collaborationStatus: str = Field(description="'HEALTHY_COLLABORATION', 'SUSPECTED_COLLUSION', 'FREE_RIDER_DETECTED', 'HIGH_FRICTION', 'INSUFFICIENT_DATA'")
    groupPeerAverage: Optional[float] = None
    peerRatingCount: int = 0
    aiStatus: str = Field(default="OK", description="'OK', 'PARTIAL', 'UNAVAILABLE' or 'NOT_USED'")
    aiNotes: List[str] = Field(default_factory=list, description="What the AI could not do, in plain words")
    executiveSummary: str
    memberMetrics: List[MemberMetric] = Field(default_factory=list)
    anomalyFlags: List[AnomalyFlag] = Field(default_factory=list)
    recommendedActions: List[str] = Field(default_factory=list)

