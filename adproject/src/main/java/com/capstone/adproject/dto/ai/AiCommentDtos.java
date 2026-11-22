package com.capstone.adproject.dto.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Request and response of the AI service's comment check (advice for a moderator; it never approves or rejects). */
public class AiCommentDtos {

    /** The longest comment the AI service accepts (its schema rejects more, and would fail the whole batch of 20 with it). */
    public static final int MAX_TEXT_CHARS = 4000;

    /** One comment to read. Text beyond the limit is cut here, so a single very long comment cannot stop a scan. */
    public record Item(@JsonProperty("id") String id, @JsonProperty("text") String text) {
        public Item {
            if (text != null && text.length() > MAX_TEXT_CHARS) {
                int end = MAX_TEXT_CHARS;
                if (Character.isHighSurrogate(text.charAt(end - 1))) {
                    end--; // do not leave half an emoji at the cut
                }
                text = text.substring(0, end);
            }
        }
    }

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
