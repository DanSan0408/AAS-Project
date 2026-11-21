package com.capstone.adproject.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.capstone.adproject.model.AssessmentComment;
import com.capstone.adproject.model.AssessmentComment.ApprovalStatus;

/**
 * How often the AI comment check and the people moderating agreed. A rejection is not always about tone and an approval can be
 * leniency, so this shows where the two diverge, not a true accuracy; the annotated sample is the proper accuracy test.
 *
 * <p>Left out: comments edited after the scan (the finding was about other text), and comments approved in bulk (nobody read them
 * one by one, so approving a comment the AI called fine would count as agreement by construction).
 */
public final class AiAgreement {

    /** Below this many decisions the percentage is shown but not trusted. */
    public static final int ENOUGH_DECISIONS = 30;
    private static final Set<String> REVIEW_LABELS = Set.of("abusive", "sarcastic", "inappropriate", "unclear", "injection_attempt");
    private static final double REVIEW_BELOW_CONFIDENCE = 0.7;
    private static final List<String> LABEL_ORDER =
            List.of("ok", "negative_constructive", "unclear", "sarcastic", "inappropriate", "abusive", "injection_attempt");

    private AiAgreement() {}

    public record LabelRow(String label, String text, int approved, int rejected) {
        public int total() {
            return approved + rejected;
        }
    }

    public record Report(int compared, int agreed, int flaggedButApproved, int missedButRejected, int flaggedAndRejected,
                         int fineAndApproved, int skippedBulk, int skippedEdited, List<LabelRow> labels) {
        public Double agreementPercent() {
            return compared == 0 ? null : agreed * 100.0 / compared;
        }

        public boolean enough() {
            return compared >= ENOUGH_DECISIONS;
        }
    }

    /** Would the check have asked a moderator to look at this comment? Mirrors the AI service's rule. */
    public static boolean wasFlagged(AssessmentComment c) {
        return REVIEW_LABELS.contains(c.getAiLabel()) || (c.getAiConfidence() != null && c.getAiConfidence() < REVIEW_BELOW_CONFIDENCE);
    }

    public static Report compare(List<AssessmentComment> comments) {
        int flaggedApproved = 0, missedRejected = 0, flaggedRejected = 0, fineApproved = 0, bulk = 0, edited = 0;
        Map<String, int[]> byLabel = new LinkedHashMap<>();
        LABEL_ORDER.forEach(l -> byLabel.put(l, new int[2]));
        for (AssessmentComment c : comments) {
            boolean approved = c.getApprovalStatus() == ApprovalStatus.APPROVED;
            boolean rejected = c.getApprovalStatus() == ApprovalStatus.REJECTED;
            if (c.getAiCheckedAt() == null || c.getAiLabel() == null || !(approved || rejected)) {
                continue;
            }
            if (AssessmentCommentService.needsScan(c)) {
                edited++;
                continue;
            }
            if (approved && c.isBulkDecision()) {
                bulk++;
                continue;
            }
            boolean flagged = wasFlagged(c);
            if (flagged && approved) flaggedApproved++;
            else if (flagged) flaggedRejected++;
            else if (approved) fineApproved++;
            else missedRejected++;
            int[] row = byLabel.computeIfAbsent(c.getAiLabel(), l -> new int[2]);
            row[approved ? 0 : 1]++;
        }
        int compared = flaggedApproved + missedRejected + flaggedRejected + fineApproved;
        List<LabelRow> rows = new ArrayList<>();
        byLabel.forEach((label, n) -> {
            if (n[0] + n[1] > 0) rows.add(new LabelRow(label, text(label), n[0], n[1]));
        });
        return new Report(compared, flaggedRejected + fineApproved, flaggedApproved, missedRejected, flaggedRejected, fineApproved,
                bulk, edited, rows);
    }

    private static String text(String label) {
        return switch (label) {
            case "ok" -> "Fine";
            case "negative_constructive" -> "Critical but civil";
            case "abusive" -> "Insult or abuse";
            case "sarcastic" -> "Sarcasm";
            case "inappropriate" -> "Inappropriate";
            case "injection_attempt" -> "Tries to instruct the system";
            case "unclear" -> "Needs a human look";
            default -> label;
        };
    }
}
