package com.capstone.adproject.service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.capstone.adproject.repositories.AssessmentCommentRepository;

/**
 * Has the AI read new pending comments on its own, so moderators find them already marked. It only records advice: nothing
 * is approved, rejected or hidden. Off unless {@code app.comment-scan.enabled} is true.
 */
@Service
public class CommentScanScheduler {

    private static final Logger log = LoggerFactory.getLogger(CommentScanScheduler.class);
    /** Most comments one run reads (the AI client allows only so many model calls per window). */
    static final int MAX_COMMENTS_PER_RUN = 200;

    /** What the last run did, for the super admin page. */
    public record RunSummary(Instant at, boolean manual, int assessments, int read, int flagged, int notChecked, int remaining,
                             String status, String note) {}

    private final AssessmentCommentRepository commentRepository;
    private final AssessmentCommentService commentService;
    private final boolean enabled;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile RunSummary last;

    @Autowired(required = false)
    private AiServiceClient aiServiceClient;

    public CommentScanScheduler(AssessmentCommentRepository commentRepository, AssessmentCommentService commentService,
                                @Value("${app.comment-scan.enabled:false}") boolean enabled) {
        this.commentRepository = commentRepository;
        this.commentService = commentService;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public RunSummary lastRun() {
        return last;
    }

    @Scheduled(fixedDelayString = "${app.comment-scan.interval-ms:900000}", initialDelayString = "${app.comment-scan.initial-delay-ms:180000}")
    public void scheduledRun() {
        if (enabled) {
            runOnce(false);
        }
    }

    /** Reads what is new, oldest assessment first, up to the per-run limit. Never throws; the outcome is in the summary. */
    public RunSummary runOnce(boolean manual) {
        if (aiServiceClient == null) {
            return record(new RunSummary(Instant.now(), manual, 0, 0, 0, 0, 0, "SKIPPED", "No AI service is configured."));
        }
        if (!running.compareAndSet(false, true)) {
            return new RunSummary(Instant.now(), manual, 0, 0, 0, 0, 0, "SKIPPED", "A scan is already running.");
        }
        int assessments = 0, read = 0, flagged = 0, notChecked = 0, remaining = 0;
        String status = "OK";
        String note = "";
        try {
            List<Long> ids = commentRepository.findAssessmentIdsWithPendingComments().stream().distinct().sorted().toList();
            for (Long assessmentId : ids) {
                if (read + notChecked >= MAX_COMMENTS_PER_RUN) {
                    status = "PARTIAL";
                    note = "Stopped at the per-run limit; the rest is read next time.";
                    break;
                }
                AssessmentCommentService.ScanResult result = commentService.scanPendingComments(assessmentId);
                if (result.checked() + result.notChecked() > 0) assessments++;
                read += result.checked();
                flagged += result.flagged();
                notChecked += result.notChecked();
                remaining += result.remaining();
                if ("UNAVAILABLE".equals(result.aiStatus()) || "PARTIAL".equals(result.aiStatus())) {
                    status = result.aiStatus();
                    note = "The AI could not read everything; it will be retried next time.";
                    if ("UNAVAILABLE".equals(result.aiStatus())) break;
                }
            }
        } catch (AiServiceClient.RateLimitedException e) {
            status = "PARTIAL";
            note = "Held back by the AI call limit; continues next time.";
        } catch (RuntimeException e) {
            status = "FAILED";
            note = "The scan could not run (" + e.getClass().getSimpleName() + ").";
            log.warn("Scheduled comment scan failed: {}", e.getClass().getSimpleName());
        } finally {
            running.set(false);
        }
        if (read > 0 || notChecked > 0) {
            log.info("Comment scan: read={}, flagged={}, notChecked={}, remaining={}, status={}", read, flagged, notChecked, remaining, status);
        }
        return record(new RunSummary(Instant.now(), manual, assessments, read, flagged, notChecked, remaining, status, note));
    }

    private RunSummary record(RunSummary summary) {
        last = summary;
        return summary;
    }
}
