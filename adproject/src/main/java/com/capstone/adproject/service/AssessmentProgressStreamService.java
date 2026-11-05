package com.capstone.adproject.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.capstone.adproject.model.Assessment;

@Service
public class AssessmentProgressStreamService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentProgressStreamService.class);
    private static final Long SSE_TIMEOUT = 30 * 60 * 1000L; // 30 minutes

    private final Map<Long, List<SseEmitter>> assessmentEmitters = new ConcurrentHashMap<>();
    private final AssessmentService assessmentService;
    private final ProgressTrackingService progressTrackingService;

    public AssessmentProgressStreamService(
            AssessmentService assessmentService,
            ProgressTrackingService progressTrackingService) {
        this.assessmentService = assessmentService;
        this.progressTrackingService = progressTrackingService;
    }

    public SseEmitter subscribe(Long assessmentId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        assessmentEmitters.computeIfAbsent(assessmentId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> {
            List<SseEmitter> list = assessmentEmitters.get(assessmentId);
            if (list != null) {
                list.remove(emitter);
                if (list.isEmpty()) {
                    assessmentEmitters.remove(assessmentId);
                }
            }
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        // Send initial snapshot immediately
        sendSnapshotToEmitter(assessmentId, emitter);

        return emitter;
    }

    public void broadcastProgress(Long assessmentId) {
        if (assessmentId == null) return;
        List<SseEmitter> emitters = assessmentEmitters.get(assessmentId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        Map<String, Object> payload = buildProgressPayload(assessmentId);
        if (payload == null) return;

        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("progress-update")
                        .data(payload));
            } catch (IOException | IllegalStateException e) {
                deadEmitters.add(emitter);
            }
        }
        if (!deadEmitters.isEmpty()) {
            emitters.removeAll(deadEmitters);
        }
    }

    private void sendSnapshotToEmitter(Long assessmentId, SseEmitter emitter) {
        Map<String, Object> payload = buildProgressPayload(assessmentId);
        if (payload != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("progress-init")
                        .data(payload));
            } catch (IOException e) {
                log.debug("Failed to send initial SSE payload: {}", e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> buildProgressPayload(Long assessmentId) {
        Optional<Assessment> assessmentOpt = assessmentService.getAssessmentById(assessmentId);
        if (assessmentOpt.isEmpty()) {
            return null;
        }
        Assessment assessment = assessmentOpt.get();

        Map<String, Object> lecturerProgress = progressTrackingService.getLecturerProgress(assessment);
        List<Map<String, Object>> progressList = (List<Map<String, Object>>) lecturerProgress.getOrDefault("progressList", Collections.emptyList());

        int totalAssessors = progressList.size();
        int completedAssessors = 0;
        int sumProgress = 0;

        for (Map<String, Object> item : progressList) {
            Number progNum = (Number) item.getOrDefault("progress", 0);
            int prog = progNum != null ? progNum.intValue() : 0;
            sumProgress += prog;
            if ("Completed".equalsIgnoreCase(String.valueOf(item.get("status")))) {
                completedAssessors++;
            }
        }

        int overallPercent = totalAssessors > 0 ? (sumProgress / totalAssessors) : 0;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assessmentId", assessment.getId());
        payload.put("assessmentTitle", assessment.getTitle());
        payload.put("overallPercent", overallPercent);
        payload.put("totalAssessors", totalAssessors);
        payload.put("completedAssessors", completedAssessors);
        payload.put("details", progressList);
        payload.put("lastUpdated", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));

        return payload;
    }
}

