package com.capstone.adproject.dto.ai;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AiTeamDynamicsDtos {

    public record MemberOverview(
            @JsonProperty("studentId") Long studentId,
            @JsonProperty("studentName") String studentName,
            @JsonProperty("email") String email
    ) {}

    public record PeerRatingItem(
            @JsonProperty("evaluatorId") Long evaluatorId,
            @JsonProperty("evaluatorName") String evaluatorName,
            @JsonProperty("evaluatedId") Long evaluatedId,
            @JsonProperty("evaluatedName") String evaluatedName,
            @JsonProperty("criterionLabel") String criterionLabel,
            @JsonProperty("earned") Double earned,
            @JsonProperty("possible") Double possible
    ) {}

    public record PeerCommentItem(
            @JsonProperty("evaluatorName") String evaluatorName,
            @JsonProperty("evaluatedName") String evaluatedName,
            @JsonProperty("question") String question,
            @JsonProperty("commentText") String commentText
    ) {}

    public record AuditRequest(
            @JsonProperty("groupId") Long groupId,
            @JsonProperty("groupName") String groupName,
            @JsonProperty("assessmentId") Long assessmentId,
            @JsonProperty("assessmentTitle") String assessmentTitle,
            @JsonProperty("members") List<MemberOverview> members,
            @JsonProperty("peerRatings") List<PeerRatingItem> peerRatings,
            @JsonProperty("peerComments") List<PeerCommentItem> peerComments
    ) {}

    public record MemberMetric(
            @JsonProperty("studentId") Long studentId,
            @JsonProperty("studentName") String studentName,
            @JsonProperty("selfScoreAvg") Double selfScoreAvg,
            @JsonProperty("peerScoreAvg") Double peerScoreAvg,
            @JsonProperty("selfInflationGap") Double selfInflationGap,
            @JsonProperty("status") String status,
            @JsonProperty("contributionSummary") String contributionSummary
    ) {}

    public record AnomalyFlag(
            @JsonProperty("type") String type,
            @JsonProperty("severity") String severity,
            @JsonProperty("targetStudent") String targetStudent,
            @JsonProperty("description") String description
    ) {}

    public record AuditResponse(
            @JsonProperty("groupId") Long groupId,
            @JsonProperty("groupName") String groupName,
            @JsonProperty("teamHealthScore") Integer teamHealthScore,
            @JsonProperty("collaborationStatus") String collaborationStatus,
            @JsonProperty("executiveSummary") String executiveSummary,
            @JsonProperty("memberMetrics") List<MemberMetric> memberMetrics,
            @JsonProperty("anomalyFlags") List<AnomalyFlag> anomalyFlags,
            @JsonProperty("recommendedActions") List<String> recommendedActions
    ) {}
}

