package com.capstone.adproject.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.capstone.adproject.dto.analytics.LecturerComparisonData;
import com.capstone.adproject.dto.analytics.LecturerComparisonData.Option;
import com.capstone.adproject.service.CourseScopeService;
import com.capstone.adproject.service.LecturerAnalyticsService;

class LecturerAnalyticsControllerTest {
    CourseScopeService scope;
    LecturerAnalyticsService analytics;
    MockMvc mvc;
    @BeforeEach void setup() {
        scope = mock(CourseScopeService.class); analytics = mock(LecturerAnalyticsService.class);
        when(scope.getActiveCourseIdForCurrentUser()).thenReturn(null);
        mvc = MockMvcBuilders.standaloneSetup(new LecturerAnalyticsController(scope, analytics)).build();
    }
    @Test void pageUsesOnlyTheActiveCourseOptionsAndRejectsStaleSelection() throws Exception {
        when(scope.getActiveCourseIdForCurrentUser()).thenReturn(10L);
        when(analytics.assessmentsForCourse(10L)).thenReturn(List.of(new Option("20", "Review")));
        mvc.perform(get("/admin/analytics/lecturer-comparison").param("assessmentId", "999"))
            .andExpect(status().isOk()).andExpect(view().name("lecturer_comparison_analytics"))
            .andExpect(model().attribute("selectedAssessmentId", "20"))
            .andExpect(header().string("Cache-Control", "no-store"));
    }
    @Test void dataWithoutAnActiveCourseIsForbidden() throws Exception {
        mvc.perform(get("/admin/analytics/lecturer-comparison/data").param("assessmentId", "20"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").exists());
        verifyNoInteractions(analytics);
    }
    @Test void anotherCoursesAssessmentReturns404WithoutDetails() throws Exception {
        when(scope.getActiveCourseIdForCurrentUser()).thenReturn(10L);
        when(analytics.comparison(10L, 999L)).thenThrow(new IllegalArgumentException("private title"));
        mvc.perform(get("/admin/analytics/lecturer-comparison/data").param("assessmentId", "999"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("This assessment is not available in your active course."));
    }
    @Test void committedComparisonIsJsonAndNeverCached() throws Exception {
        when(scope.getActiveCourseIdForCurrentUser()).thenReturn(10L);
        when(analytics.comparison(10L, 20L)).thenReturn(new LecturerComparisonData(20L, "Review", List.of(), List.of(), List.of(), 0));
        mvc.perform(get("/admin/analytics/lecturer-comparison/data").param("assessmentId", "20"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.assessmentId").value(20))
            .andExpect(jsonPath("$.scores").isArray()).andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test void moderationBriefingWithoutActiveCourseIsForbidden() throws Exception {
        mvc.perform(get("/admin/analytics/lecturer-comparison/moderation-briefing").param("assessmentId", "20"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").exists());
    }
}
