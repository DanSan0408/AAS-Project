package com.capstone.adproject.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import com.capstone.adproject.export.ReportRequest;
import com.capstone.adproject.export.ReportFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.capstone.adproject.dto.ai.AiModerationDtos;
import com.capstone.adproject.dto.analytics.LecturerComparisonData;
import com.capstone.adproject.dto.analytics.LecturerComparisonData.Option;
import com.capstone.adproject.service.AiServiceClient;
import com.capstone.adproject.service.CourseScopeService;
import com.capstone.adproject.service.LecturerAnalyticsService;

import jakarta.servlet.http.HttpServletResponse;

/** Admin/super-admin access is enforced by the existing /admin/** security rule. */
@Controller
@RequestMapping("/admin/analytics/lecturer-comparison")
public class LecturerAnalyticsController {
    private final CourseScopeService scope;
    private final LecturerAnalyticsService analytics;
    private final AiServiceClient aiServiceClient;
    private final com.capstone.adproject.service.AssessmentService assessmentService;
    private final com.capstone.adproject.service.AssessmentCommentService commentService;

    public LecturerAnalyticsController(CourseScopeService scope, LecturerAnalyticsService analytics) {
        this(scope, analytics, null, null, null);
    }

    @Autowired
    public LecturerAnalyticsController(
            CourseScopeService scope,
            LecturerAnalyticsService analytics,
            @Autowired(required = false) AiServiceClient aiServiceClient,
            @Autowired(required = false) com.capstone.adproject.service.AssessmentService assessmentService,
            @Autowired(required = false) com.capstone.adproject.service.AssessmentCommentService commentService) {
        this.scope = scope;
        this.analytics = analytics;
        this.aiServiceClient = aiServiceClient;
        this.assessmentService = assessmentService;
        this.commentService = commentService;
    }

    @GetMapping
    public String page(@RequestParam(required = false) Long assessmentId, Model model, HttpServletResponse response) {
        Long courseId = scope.getActiveCourseIdForCurrentUser();
        List<Option> options = analytics.assessmentsForCourse(courseId);
        String requested = assessmentId == null ? null : assessmentId.toString();
        String selected = options.stream().map(Option::id).filter(id -> id.equals(requested)).findFirst()
            .orElse(options.isEmpty() ? "" : options.get(0).id());
        model.addAttribute("analyticsAssessments", options);
        model.addAttribute("selectedAssessmentId", selected);
        response.setHeader("Cache-Control", "no-store");
        return "lecturer_comparison_analytics";
    }

    @GetMapping("/data")
    public ResponseEntity<?> data(@RequestParam Long assessmentId) {
        Long courseId = scope.getActiveCourseIdForCurrentUser();
        if (courseId == null) return ResponseEntity.status(403).cacheControl(CacheControl.noStore())
            .body(Map.of("message", "Select an active course before viewing lecturer comparisons."));
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(analytics.comparison(courseId, assessmentId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).cacheControl(CacheControl.noStore())
                .body(Map.of("message", "This assessment is not available in your active course."));
        }
    }

    /** Largest request body accepted for a download: the tables on the page, far below this, so anything bigger is refused. */
    static final int MAX_EXPORT_BODY_BYTES = 5 * 1024 * 1024;
    private static final com.fasterxml.jackson.databind.ObjectMapper EXPORT_JSON = new com.fasterxml.jackson.databind.ObjectMapper()
        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Turns the tables the page is showing into a downloadable .xlsx or .pdf. The comparison is worked out in the browser, so
     * the browser sends back exactly what it shows and this only lays it out. The body is read here with a hard size limit
     * (so a huge request is never loaded into memory), every part is checked ({@link ReportRequest}), and the file name is
     * chosen here, never taken from the request.
     */
    @PostMapping(value = "/export", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> export(@RequestParam("format") String format, HttpServletRequest http) {
        if (scope.getActiveCourseIdForCurrentUser() == null) {
            return exportError(403, "Select an active course before exporting lecturer comparisons.");
        }
        if (http.getContentLengthLong() > MAX_EXPORT_BODY_BYTES) {
            return exportError(413, "That export is too large. Narrow the filters and try again.");
        }
        byte[] body;
        try {
            body = http.getInputStream().readNBytes(MAX_EXPORT_BODY_BYTES + 1);
        } catch (java.io.IOException e) {
            return exportError(400, "The export request could not be read.");
        }
        if (body.length > MAX_EXPORT_BODY_BYTES) {
            return exportError(413, "That export is too large. Narrow the filters and try again.");
        }
        try {
            ReportFormat reportFormat = ReportFormat.parse(format);
            ReportRequest request;
            try {
                request = EXPORT_JSON.readValue(body, ReportRequest.class);
            } catch (java.io.IOException e) {
                throw new IllegalArgumentException("The export request is not valid.");
            }
            if (request == null) {
                throw new IllegalArgumentException("There is nothing to export.");
            }
            byte[] file = reportFormat.write(request.toDocument());
            String filename = "marking-consistency-" + java.time.LocalDate.now() + "." + reportFormat.extension();
            return ResponseEntity.ok()
                .contentType(reportFormat.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(file);
        } catch (IllegalArgumentException ex) {
            return exportError(400, ex.getMessage());
        }
    }

    private static ResponseEntity<Map<String, String>> exportError(int status, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("message", message));
    }

    @GetMapping("/moderation-briefing")
    public ResponseEntity<?> moderationBriefing(@RequestParam Long assessmentId) {
        Long courseId = scope.getActiveCourseIdForCurrentUser();
        if (courseId == null) {
            return ResponseEntity.status(403).cacheControl(CacheControl.noStore())
                .body(Map.of("message", "Select an active course before viewing lecturer comparisons."));
        }
        try {
            LecturerComparisonData data = analytics.comparison(courseId, assessmentId);
            List<Map<String, Object>> criteriaList = data.criteria().stream()
                .map(c -> Map.<String, Object>of(
                    "id", c.id(),
                    "rubricId", c.rubricId() != null ? c.rubricId() : 0,
                    "rubricName", c.rubricName() != null ? c.rubricName() : "",
                    "label", c.label() != null ? c.label() : "",
                    "clo", c.clo() != null ? c.clo() : 0,
                    "scope", c.scope() != null ? c.scope() : "",
                    "possible", c.possible(),
                    "step", c.step()
                )).toList();

            List<Map<String, Object>> scoresList = data.scores().stream()
                .map(s -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("lecturerId", s.lecturerId());
                    map.put("lecturerName", s.lecturerName() != null ? s.lecturerName() : "Lecturer " + s.lecturerId());
                    map.put("teamId", s.teamId());
                    map.put("teamName", s.teamName());
                    map.put("studentId", s.studentId());
                    map.put("studentName", s.studentName());
                    map.put("targetId", s.targetId());
                    map.put("targetName", s.targetName());
                    map.put("criterionId", s.criterionId());
                    map.put("earned", s.earned());
                    map.put("status", s.status());
                    map.put("submittedAt", s.submittedAt());
                    return map;
                }).toList();

            AiModerationDtos.BriefingRequest request = new AiModerationDtos.BriefingRequest(
                data.assessmentId(),
                data.assessmentTitle(),
                criteriaList,
                scoresList,
                briefingComments(assessmentId, data)
            );

            AiModerationDtos.BriefingResponse briefing = aiServiceClient.generateModerationBriefing(request);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(briefing);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).cacheControl(CacheControl.noStore())
                .body(Map.of("message", "This assessment is not available in your active course."));
        } catch (AiServiceClient.RateLimitedException ex) {
            return ResponseEntity.status(429).cacheControl(CacheControl.noStore())
                .body(Map.of("message", "You have asked for several AI analyses in a short time. Please wait a few minutes and try again."));
        } catch (Exception ex) {
            return ResponseEntity.status(502).cacheControl(CacheControl.noStore())
                .body(Map.of("message", "The AI briefing is unavailable right now. The calculated figures on this page are not affected."));
        }
    }

    /**
     * Assessors' comments on the work being compared, each tied to the same target name the scores use (a team, or one student),
     * so the briefing can put the comments about the most contested work first. Comments that cannot be tied to a target are left out.
     */
    private List<AiModerationDtos.CommentItem> briefingComments(Long assessmentId, LecturerComparisonData data) {
        if (assessmentService == null || commentService == null) {
            return List.of();
        }
        var assessment = assessmentService.getAssessmentById(assessmentId);
        if (assessment.isEmpty()) {
            return List.of();
        }
        Map<Long, String> targetByStudent = new HashMap<>();
        Map<Long, String> targetByTeam = new HashMap<>();
        data.scores().forEach(s -> {
            if (s.studentId() != null) {
                targetByStudent.putIfAbsent(s.studentId(), s.targetName());
            } else if (s.teamId() != null) {
                targetByTeam.putIfAbsent(s.teamId(), s.targetName());
            }
        });
        List<AiModerationDtos.CommentItem> items = new java.util.ArrayList<>();
        for (var comment : commentService.getAssessorCommentsForBriefing(assessment.get())) {
            var student = comment.getEvaluatedStudent();
            String target = targetByStudent.get(student.getId());
            if (target == null && student.getGroup() != null) {
                target = targetByTeam.get(student.getGroup().getId());
            }
            if (target != null) {
                items.add(new AiModerationDtos.CommentItem(
                    comment.getEvaluatorName() != null ? comment.getEvaluatorName() : "Assessor",
                    target,
                    comment.getCommentLabel(),
                    comment.getCommentText()));
            }
        }
        return items;
    }
}
