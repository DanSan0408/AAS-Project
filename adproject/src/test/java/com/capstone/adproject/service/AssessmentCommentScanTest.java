package com.capstone.adproject.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.capstone.adproject.dto.ai.AiCommentDtos;
import com.capstone.adproject.model.AssessmentComment;
import com.capstone.adproject.model.AssessmentComment.ApprovalStatus;
import com.capstone.adproject.repositories.AssessmentCommentRepository;
import com.capstone.adproject.repositories.StudentRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The AI comment check is advice: it marks comments for a person and never changes who may see them. */
@ExtendWith(MockitoExtension.class)
class AssessmentCommentScanTest {

    @Mock private AssessmentCommentRepository commentRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private AiServiceClient aiServiceClient;

    @InjectMocks private AssessmentCommentService service;

    private long nextId = 1;

    private AssessmentComment pending(String text) {
        AssessmentComment c = new AssessmentComment();
        c.setId(nextId++);
        c.setCommentText(text);
        c.setApprovalStatus(ApprovalStatus.PENDING);
        c.setIsApproved(false);
        return c;
    }

    private static AiCommentDtos.Verdict verdict(AssessmentComment c, String label, double confidence, boolean flagged, String reason) {
        return new AiCommentDtos.Verdict(String.valueOf(c.getId()), label, confidence, "en", reason, flagged, flagged, "AI");
    }

    private static AiCommentDtos.CheckResponse response(String status, AiCommentDtos.Verdict... verdicts) {
        return new AiCommentDtos.CheckResponse(List.of(verdicts), status, List.of());
    }

    @BeforeEach
    void stubSave() {
        org.mockito.Mockito.lenient().when(commentRepository.save(any(AssessmentComment.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void aScanRecordsTheFindingAndFlagsWhatNeedsALookWithoutTouchingApproval() {
        AssessmentComment rude = pending("You are useless");
        AssessmentComment fine = pending("Good teamwork");
        when(commentRepository.findByAssessmentId(5L)).thenReturn(List.of(rude, fine));
        when(aiServiceClient.checkComments(any())).thenReturn(response("OK",
                verdict(rude, "abusive", 0.9, true, "Insults a teammate"),
                verdict(fine, "ok", 0.95, false, "Constructive")));

        AssessmentCommentService.ScanResult result = service.scanPendingComments(5L);

        assertEquals(2, result.checked());
        assertEquals(1, result.flagged());
        assertTrue(rude.getIsFlaggedNegative());
        assertEquals("AI check: Insults a teammate", rude.getFlagReason());
        assertEquals("abusive", rude.getAiLabel());
        assertFalse(fine.getIsFlaggedNegative());
        assertEquals(ApprovalStatus.PENDING, rude.getApprovalStatus());
        assertEquals(ApprovalStatus.PENDING, fine.getApprovalStatus());
        assertFalse(rude.isApproved());
        assertFalse(fine.isApproved());
    }

    @Test
    void commentsAlreadyReadAreNotSentAgainButEditedOnesAre() {
        AssessmentComment read = pending("Nice work");
        service_markRead(read);
        AssessmentComment edited = pending("Original");
        service_markRead(edited);
        edited.setCommentText("Edited after the scan");
        when(commentRepository.findByAssessmentId(5L)).thenReturn(List.of(read, edited));
        when(aiServiceClient.checkComments(any())).thenReturn(response("OK", verdict(edited, "ok", 0.9, false, "Fine")));

        service.scanPendingComments(5L);

        ArgumentCaptor<AiCommentDtos.CheckRequest> sent = ArgumentCaptor.forClass(AiCommentDtos.CheckRequest.class);
        verify(aiServiceClient).checkComments(sent.capture());
        assertEquals(1, sent.getValue().comments().size());
        assertEquals(String.valueOf(edited.getId()), sent.getValue().comments().get(0).id());
    }

    private void service_markRead(AssessmentComment c) {
        c.setAiLabel("ok");
        c.setAiConfidence(0.9);
        c.setAiCheckedAt(java.time.LocalDateTime.now());
        c.setAiTextHash(AssessmentCommentService.textHash(c.getCommentText()));
    }

    @Test
    void approvedRejectedAndBlankCommentsAreNotScanned() {
        AssessmentComment approved = pending("Approved already");
        approved.setIsApproved(true);
        approved.setApprovalStatus(ApprovalStatus.APPROVED);
        AssessmentComment rejected = pending("Rejected before");
        rejected.setApprovalStatus(ApprovalStatus.REJECTED);
        AssessmentComment blank = pending("   ");
        when(commentRepository.findByAssessmentId(5L)).thenReturn(List.of(approved, rejected, blank));

        AssessmentCommentService.ScanResult result = service.scanPendingComments(5L);

        assertEquals(0, result.checked());
        verify(aiServiceClient, never()).checkComments(any());
    }

    @Test
    void anAdminsOwnFlagIsNotOverwrittenByTheAi() {
        AssessmentComment c = pending("Hmm");
        c.setIsFlaggedNegative(true);
        c.setFlagReason("Manually flagged by administrator");
        when(commentRepository.findByAssessmentId(5L)).thenReturn(List.of(c));
        when(aiServiceClient.checkComments(any())).thenReturn(response("OK", verdict(c, "ok", 0.95, false, "Fine")));

        service.scanPendingComments(5L);

        assertTrue(c.getIsFlaggedNegative());
        assertEquals("Manually flagged by administrator", c.getFlagReason());
        assertEquals("ok", c.getAiLabel(), "the finding is still recorded beside the admin's flag");
    }

    @Test
    void aCommentTheAiCouldNotCheckStaysUnscannedSoALaterScanRetriesIt() {
        AssessmentComment c = pending("Something");
        when(commentRepository.findByAssessmentId(5L)).thenReturn(List.of(c));
        when(aiServiceClient.checkComments(any())).thenReturn(response("UNAVAILABLE",
                new AiCommentDtos.Verdict(String.valueOf(c.getId()), "not_checked", 0.0, null, "AI unavailable", true, false, "NONE")));

        AssessmentCommentService.ScanResult result = service.scanPendingComments(5L);

        assertEquals(0, result.checked());
        assertEquals(1, result.notChecked());
        assertEquals("UNAVAILABLE", result.aiStatus());
        assertEquals(null, c.getAiCheckedAt());
        assertTrue(AssessmentCommentService.needsScan(c));
        verify(commentRepository, never()).save(any());
    }

    @Test
    void aScanThatCannotReachTheAiFailsInsteadOfPretendingToHaveRun() {
        AssessmentComment c = pending("Something");
        when(commentRepository.findByAssessmentId(5L)).thenReturn(List.of(c));
        when(aiServiceClient.checkComments(any())).thenThrow(new RuntimeException("down"));

        assertThrows(RuntimeException.class, () -> service.scanPendingComments(5L));
        assertEquals(null, c.getAiLabel());
    }

    @Test
    void aScanReadsAtMostOneHundredCommentsAndSaysHowManyAreLeft() {
        List<AssessmentComment> many = new ArrayList<>();
        for (int i = 0; i < 130; i++) many.add(pending("comment " + i));
        when(commentRepository.findByAssessmentId(5L)).thenReturn(many);
        when(aiServiceClient.checkComments(any())).thenAnswer(invocation -> {
            AiCommentDtos.CheckRequest request = invocation.getArgument(0);
            assertTrue(request.comments().size() <= 20);
            return new AiCommentDtos.CheckResponse(request.comments().stream()
                .map(item -> new AiCommentDtos.Verdict(item.id(), "ok", 0.9, "en", "Fine", false, false, "AI")).toList(), "OK", List.of());
        });

        AssessmentCommentService.ScanResult result = service.scanPendingComments(5L);

        assertEquals(100, result.checked());
        assertEquals(30, result.remaining());
    }

    @Test
    void onlyConfidentlyFineScannedCommentsAreApprovedAsSafe() {
        AssessmentComment safe = pending("Great");
        service_markRead(safe);
        AssessmentComment lowConfidence = pending("Okay-ish");
        service_markRead(lowConfidence);
        lowConfidence.setAiConfidence(0.6);
        AssessmentComment flagged = pending("Rude");
        service_markRead(flagged);
        flagged.setIsFlaggedNegative(true);
        AssessmentComment unscanned = pending("Never read");
        AssessmentComment edited = pending("Was fine");
        service_markRead(edited);
        edited.setCommentText("Now different");
        AssessmentComment rejected = pending("Hidden");
        service_markRead(rejected);
        rejected.setApprovalStatus(ApprovalStatus.REJECTED);
        when(commentRepository.findByAssessmentId(5L)).thenReturn(List.of(safe, lowConfidence, flagged, unscanned, edited, rejected));

        int count = service.bulkApproveSafeComments(5L, "admin");

        assertEquals(1, count);
        assertTrue(safe.isApproved());
        assertEquals("admin", safe.getApprovedBy());
        assertTrue(safe.isBulkDecision(), "marked so the agreement report does not count it as a person agreeing with the AI");
        assertFalse(lowConfidence.isApproved());
        assertFalse(flagged.isApproved());
        assertFalse(unscanned.isApproved());
        assertFalse(edited.isApproved());
        assertFalse(rejected.isApproved());
    }

    @Test
    void approvingEverythingPendingDoesNotBringBackRejectedComments() {
        AssessmentComment waiting = pending("Waiting");
        AssessmentComment rejected = pending("Rejected by a moderator");
        rejected.setApprovalStatus(ApprovalStatus.REJECTED);
        when(commentRepository.findByAssessmentId(5L)).thenReturn(List.of(waiting, rejected));

        int count = service.bulkApproveAllComments(5L, "admin");

        assertEquals(1, count);
        assertTrue(waiting.isApproved());
        assertTrue(waiting.isBulkDecision());
        assertFalse(rejected.isApproved());
        assertEquals(ApprovalStatus.REJECTED, rejected.getApprovalStatus());
    }

    @Test
    void moderationStatsCountRealFlagsAndUnscannedComments() {
        AssessmentComment flagged = pending("Rude");
        service_markRead(flagged);
        flagged.setIsFlaggedNegative(true);
        AssessmentComment unscanned = pending("Never read");
        AssessmentComment scannedFine = pending("Fine");
        service_markRead(scannedFine);
        when(commentRepository.findByAssessmentId(5L)).thenReturn(List.of(flagged, unscanned, scannedFine));

        Map<String, Long> stats = service.getModerationStats(5L);

        assertEquals(1L, stats.get("flagged"));
        assertEquals(1L, stats.get("notScanned"));
        assertEquals(3L, stats.get("pending"));
    }

    @Test
    void theScanIsRefusedWhenNoAiServiceIsConfigured() {
        AssessmentCommentService bare = new AssessmentCommentService();
        assertThrows(IllegalStateException.class, () -> bare.scanPendingComments(5L));
    }

    @Test
    void identicalCommentsShareOneAiCallAndReuseAlreadyCheckedText() {
        AssessmentComment alreadyRead = pending("Good teamwork");
        service_markRead(alreadyRead);
        AssessmentComment duplicateOfRead = pending("Good teamwork");
        AssessmentComment newA = pending("Needs clearer slides");
        AssessmentComment newB = pending("Needs clearer slides");
        when(commentRepository.findByAssessmentId(5L)).thenReturn(List.of(alreadyRead, duplicateOfRead, newA, newB));
        when(aiServiceClient.checkComments(any())).thenAnswer(invocation -> {
            AiCommentDtos.CheckRequest req = invocation.getArgument(0);
            assertEquals(1, req.comments().size());
            assertEquals("Needs clearer slides", req.comments().get(0).text());
            return response("OK", new AiCommentDtos.Verdict(req.comments().get(0).id(), "ok", 0.92, "en", "Constructive", false, false, "AI"));
        });

        AssessmentCommentService.ScanResult result = service.scanPendingComments(5L);

        assertEquals(3, result.checked());
        assertEquals("ok", duplicateOfRead.getAiLabel());
        assertEquals("ok", newA.getAiLabel());
        assertEquals("ok", newB.getAiLabel());
        verify(aiServiceClient, org.mockito.Mockito.times(1)).checkComments(any());
    }
}
