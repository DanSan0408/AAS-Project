package com.capstone.adproject.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.capstone.adproject.service.AssessmentProgressStreamService;

@RestController
@RequestMapping("/api/progress")
public class AssessmentProgressSseController {

    private final AssessmentProgressStreamService progressStreamService;

    public AssessmentProgressSseController(AssessmentProgressStreamService progressStreamService) {
        this.progressStreamService = progressStreamService;
    }

    @GetMapping(value = "/stream/{assessmentId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    public SseEmitter streamProgress(@PathVariable("assessmentId") Long assessmentId) {
        return progressStreamService.subscribe(assessmentId);
    }

    @GetMapping("/snapshot/{assessmentId}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<?> getSnapshot(@PathVariable("assessmentId") Long assessmentId) {
        var payload = progressStreamService.buildProgressPayload(assessmentId);
        if (payload == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(payload);
    }
}

