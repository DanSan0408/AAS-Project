package com.capstone.adproject.controller;

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

    public LecturerAnalyticsController(CourseScopeService scope, LecturerAnalyticsService analytics) {
        this(scope, analytics, null);
    }

    @Autowired
    public LecturerAnalyticsController(
            CourseScopeService scope,
            LecturerAnalyticsService analytics,
            @Autowired(required = false) AiServiceClient aiServiceClient) {
        this.scope = scope;
        this.analytics = analytics;
        this.aiServiceClient = aiServiceClient;
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
                    "possible", c.possible()
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
                List.of()
            );

            AiModerationDtos.BriefingResponse briefing = aiServiceClient.generateModerationBriefing(request);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(briefing);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).cacheControl(CacheControl.noStore())
                .body(Map.of("message", "This assessment is not available in your active course."));
        } catch (Exception ex) {
            return ResponseEntity.status(500).cacheControl(CacheControl.noStore())
                .body(Map.of("message", "AI Moderation Briefing error: " + ex.getMessage()));
        }
    }
}
