package com.capstone.adproject.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.service.AssessmentProgressStreamService;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.CourseScopeService;

@RestController
@RequestMapping("/api/progress")
public class AssessmentProgressSseController {

    private final AssessmentProgressStreamService progressStreamService;

    @Autowired(required = false)
    private AssessmentService assessmentService;

    @Autowired(required = false)
    private CourseScopeService courseScopeService;

    public AssessmentProgressSseController(AssessmentProgressStreamService progressStreamService) {
        this.progressStreamService = progressStreamService;
    }

    @GetMapping(value = "/stream/{assessmentId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    public SseEmitter streamProgress(@PathVariable("assessmentId") Long assessmentId) {
        verifyAssessmentAccess(assessmentId);
        return progressStreamService.subscribe(assessmentId);
    }

    @GetMapping("/snapshot/{assessmentId}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    public ResponseEntity<?> getSnapshot(@PathVariable("assessmentId") Long assessmentId) {
        verifyAssessmentAccess(assessmentId);
        var payload = progressStreamService.buildProgressPayload(assessmentId);
        if (payload == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(payload);
    }

    private void verifyAssessmentAccess(Long assessmentId) {
        if (courseScopeService == null || assessmentService == null) {
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return;
        }
        boolean isSuperAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
        if (isSuperAdmin) {
            return;
        }
        Assessment assessment = assessmentService.getAssessmentById(assessmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assessment not found"));
        Long courseId = assessment.getCourse() != null ? assessment.getCourse().getId() : null;
        if (courseId == null || !courseScopeService.isActiveCourseId(courseId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized course access");
        }
    }
}

