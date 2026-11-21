package com.capstone.adproject.service;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.capstone.adproject.model.AssessmentComment;
import com.capstone.adproject.model.AssessmentComment.ApprovalStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The agreement report must not count comments where "agreement" would be an artefact. */
class AiAgreementTest {

    private static AssessmentComment decided(String text, String label, double confidence, ApprovalStatus status) {
        AssessmentComment c = new AssessmentComment();
        c.setCommentText(text);
        c.setAiLabel(label);
        c.setAiConfidence(confidence);
        c.setAiCheckedAt(LocalDateTime.now());
        c.setAiTextHash(AssessmentCommentService.textHash(text));
        c.setApprovalStatus(status);
        return c;
    }

    @Test
    void theFourOutcomesAreCountedSeparately() {
        AiAgreement.Report report = AiAgreement.compare(List.of(
                decided("a", "abusive", 0.9, ApprovalStatus.REJECTED),          // flagged, rejected: agree
                decided("b", "ok", 0.95, ApprovalStatus.APPROVED),               // fine, approved: agree
                decided("c", "sarcastic", 0.8, ApprovalStatus.APPROVED),         // flagged but approved: false alarm
                decided("d", "ok", 0.9, ApprovalStatus.REJECTED)));              // fine but rejected: missed

        assertEquals(4, report.compared());
        assertEquals(2, report.agreed());
        assertEquals(1, report.flaggedButApproved());
        assertEquals(1, report.missedButRejected());
        assertEquals(50.0, report.agreementPercent(), 0.001);
    }

    @Test
    void aLowConfidenceFineIsTreatedAsFlaggedBecauseTheCheckAskedForALook() {
        assertTrue(AiAgreement.wasFlagged(decided("x", "ok", 0.6, ApprovalStatus.APPROVED)));
        assertFalse(AiAgreement.wasFlagged(decided("x", "ok", 0.8, ApprovalStatus.APPROVED)));
        assertFalse(AiAgreement.wasFlagged(decided("x", "negative_constructive", 0.9, ApprovalStatus.APPROVED)));
    }

    @Test
    void commentsApprovedInBulkAreLeftOutBecauseNobodyReadThem() {
        AssessmentComment bulk = decided("fine", "ok", 0.95, ApprovalStatus.APPROVED);
        bulk.setBulkDecision(true);

        AiAgreement.Report report = AiAgreement.compare(List.of(bulk));

        assertEquals(0, report.compared());
        assertEquals(1, report.skippedBulk());
        assertNull(report.agreementPercent());
    }

    @Test
    void aBulkRejectionStillCountsBecauseItWasADeliberateChoice() {
        AssessmentComment rejected = decided("rude", "abusive", 0.9, ApprovalStatus.REJECTED);
        rejected.setBulkDecision(true);

        assertEquals(1, AiAgreement.compare(List.of(rejected)).compared());
    }

    @Test
    void commentsEditedAfterTheScanAndStillPendingOnesAreNotCompared() {
        AssessmentComment edited = decided("original", "ok", 0.9, ApprovalStatus.APPROVED);
        edited.setCommentText("something else entirely");
        AssessmentComment pending = decided("waiting", "ok", 0.9, ApprovalStatus.PENDING);
        AssessmentComment unread = new AssessmentComment();
        unread.setCommentText("never read");
        unread.setApprovalStatus(ApprovalStatus.APPROVED);

        AiAgreement.Report report = AiAgreement.compare(List.of(edited, pending, unread));

        assertEquals(0, report.compared());
        assertEquals(1, report.skippedEdited());
    }

    @Test
    void thePercentageIsOnlyTrustedWithEnoughDecisions() {
        assertFalse(AiAgreement.compare(List.of(decided("a", "ok", 0.9, ApprovalStatus.APPROVED))).enough());
    }

    @Test
    void perLabelRowsShowWhatModeratorsDidWithEachFinding() {
        AiAgreement.Report report = AiAgreement.compare(List.of(
                decided("a", "abusive", 0.9, ApprovalStatus.REJECTED),
                decided("b", "abusive", 0.9, ApprovalStatus.APPROVED),
                decided("c", "ok", 0.9, ApprovalStatus.APPROVED)));

        AiAgreement.LabelRow abusive = report.labels().stream().filter(r -> r.label().equals("abusive")).findFirst().orElseThrow();
        assertEquals(1, abusive.approved());
        assertEquals(1, abusive.rejected());
        assertEquals(2, report.labels().size(), "labels nobody got are not listed");
    }
}
