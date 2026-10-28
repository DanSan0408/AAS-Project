package com.capstone.adproject.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.capstone.adproject.dto.analytics.LecturerComparisonData.Option;
import com.capstone.adproject.service.CourseScopeService;
import com.capstone.adproject.service.LecturerAnalyticsService;

import jakarta.servlet.http.HttpServletResponse;

/** Admin/super-admin access is enforced by the existing /admin/** security rule. */
@Controller
@RequestMapping("/admin/analytics/lecturer-comparison")
public class LecturerAnalyticsController {
    private final CourseScopeService scope;
    private final LecturerAnalyticsService analytics;

    public LecturerAnalyticsController(CourseScopeService scope, LecturerAnalyticsService analytics) {
        this.scope = scope;
        this.analytics = analytics;
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
}
