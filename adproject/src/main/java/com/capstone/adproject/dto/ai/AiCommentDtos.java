package com.capstone.adproject.dto.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Request and response of the AI service's comment check (advice for a moderator; it never approves or rejects). */
public class AiCommentDtos {

    public record Item(@JsonProperty("id") String id, @JsonProperty("text") String text) {}

    public record CheckRequest(@JsonProperty("comments") List<Item> comments) {}

    public record Verdict(
            @JsonProperty("id") String id,
            @JsonProperty("label") String label,
            @JsonProperty("confidence") Double confidence,
            @JsonProperty("language") String language,
            @JsonProperty("reason") String reason,
            @JsonProperty("needsHumanReview") Boolean needsHumanReview,
            @JsonProperty("flagged") Boolean flagged,
            @JsonProperty("source") String source
    ) {}

    public record CheckResponse(
            @JsonProperty("results") List<Verdict> results,
            @JsonProperty("aiStatus") String aiStatus,
            @JsonProperty("aiNotes") List<String> aiNotes
    ) {}
}
