package com.capstone.adproject.dto.ai;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AiModerationDtos {

    public record CommentItem(
            @JsonProperty("lecturerName") String lecturerName,
            @JsonProperty("targetName") String targetName,
            @JsonProperty("rubricName") String rubricName,
            @JsonProperty("commentText") String commentText
    ) {}

    public record BriefingRequest(
            @JsonProperty("assessmentId") Long assessmentId,
            @JsonProperty("assessmentTitle") String assessmentTitle,
            @JsonProperty("criteria") List<Map<String, Object>> criteria,
            @JsonProperty("scores") List<Map<String, Object>> scores,
            @JsonProperty("comments") List<CommentItem> comments
    ) {}

    public record CriterionDiscrepancy(
            @JsonProperty("criterionId") String criterionId,
            @JsonProperty("criterionLabel") String criterionLabel,
            @JsonProperty("rubricName") String rubricName,
            @JsonProperty("meanPairGapPct") Double meanPairGapPct,
            @JsonProperty("severity") String severity,
            @JsonProperty("analysis") String analysis
    ) {}

    public record TargetDiscrepancy(
            @JsonProperty("targetId") String targetId,
            @JsonProperty("targetName") String targetName,
            @JsonProperty("spreadPct") Double spreadPct,
            @JsonProperty("lecturerScores") Map<String, Double> lecturerScores,
            @JsonProperty("summaryNote") String summaryNote
    ) {}

    public record BriefingResponse(
            @JsonProperty("assessmentId") Long assessmentId,
            @JsonProperty("assessmentTitle") String assessmentTitle,
            @JsonProperty("consensusStatus") String consensusStatus,
            @JsonProperty("meanCohortGapPct") Double meanCohortGapPct,
            @JsonProperty("executiveSummary") String executiveSummary,
            @JsonProperty("topDivergentCriteria") List<CriterionDiscrepancy> topDivergentCriteria,
            @JsonProperty("priorityReviewTargets") List<TargetDiscrepancy> priorityReviewTargets,
            @JsonProperty("moderationActions") List<String> moderationActions
    ) {}
}

