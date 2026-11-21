package com.capstone.adproject.service;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.capstone.adproject.repositories.AssessmentCommentRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentScanSchedulerTest {

    @Mock private AssessmentCommentRepository commentRepository;
    @Mock private AssessmentCommentService commentService;
    @Mock private AiServiceClient aiServiceClient;

    private CommentScanScheduler scheduler(boolean enabled) {
        CommentScanScheduler s = new CommentScanScheduler(commentRepository, commentService, enabled);
        ReflectionTestUtils.setField(s, "aiServiceClient", aiServiceClient);
        return s;
    }

    private static AssessmentCommentService.ScanResult result(int checked, int flagged, int notChecked, int remaining, String status) {
        return new AssessmentCommentService.ScanResult(checked, flagged, notChecked, remaining, status, List.of());
    }

    @Test
    void aDisabledSchedulerDoesNothingOnItsTimer() {
        scheduler(false).scheduledRun();

        verify(commentRepository, never()).findAssessmentIdsWithPendingComments();
    }

    @Test
    void aRunReadsEachAssessmentWithPendingCommentsAndSumsTheOutcome() {
        when(commentRepository.findAssessmentIdsWithPendingComments()).thenReturn(List.of(7L, 3L, 3L));
        when(commentService.scanPendingComments(3L)).thenReturn(result(10, 2, 0, 0, "OK"));
        when(commentService.scanPendingComments(7L)).thenReturn(result(5, 1, 0, 0, "OK"));
        CommentScanScheduler s = scheduler(true);

        CommentScanScheduler.RunSummary run = s.runOnce(false);

        assertEquals("OK", run.status());
        assertEquals(15, run.read());
        assertEquals(3, run.flagged());
        assertNotNull(s.lastRun());
    }

    @Test
    void aRunStopsWhenTheAiIsUnavailableInsteadOfHammeringIt() {
        when(commentRepository.findAssessmentIdsWithPendingComments()).thenReturn(List.of(1L, 2L));
        when(commentService.scanPendingComments(1L)).thenReturn(result(0, 0, 4, 0, "UNAVAILABLE"));

        CommentScanScheduler.RunSummary run = scheduler(true).runOnce(false);

        assertEquals("UNAVAILABLE", run.status());
        assertEquals(4, run.notChecked());
        verify(commentService, never()).scanPendingComments(2L);
    }

    @Test
    void aFailureIsRecordedNotThrownSoTheTimerKeepsRunning() {
        when(commentRepository.findAssessmentIdsWithPendingComments()).thenReturn(List.of(1L));
        when(commentService.scanPendingComments(1L)).thenThrow(new RuntimeException("down"));

        CommentScanScheduler.RunSummary run = scheduler(true).runOnce(false);

        assertEquals("FAILED", run.status());
    }

    @Test
    void theAiCallLimitEndsTheRunAsPartialNotAsAFailure() {
        when(commentRepository.findAssessmentIdsWithPendingComments()).thenReturn(List.of(1L));
        when(commentService.scanPendingComments(1L)).thenThrow(new AiServiceClient.RateLimitedException());

        assertEquals("PARTIAL", scheduler(true).runOnce(false).status());
    }

    @Test
    void aRunStopsAtThePerRunLimit() {
        when(commentRepository.findAssessmentIdsWithPendingComments()).thenReturn(List.of(1L, 2L, 3L));
        when(commentService.scanPendingComments(anyLong())).thenReturn(result(120, 0, 0, 0, "OK"));

        CommentScanScheduler.RunSummary run = scheduler(true).runOnce(false);

        assertEquals("PARTIAL", run.status());
        assertEquals(240, run.read());
        verify(commentService, never()).scanPendingComments(3L);
    }

    @Test
    void withoutAnAiServiceTheRunIsSkipped() {
        CommentScanScheduler s = new CommentScanScheduler(commentRepository, commentService, true);

        assertEquals("SKIPPED", s.runOnce(true).status());
    }
}
