package com.capstone.adproject.controller;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.service.CohortMetrics;
import com.capstone.adproject.service.CohortMetrics.CriterionSample;
import com.capstone.adproject.service.CohortMetrics.StudentResult;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Course Analytics page: plain-language cards, honest empty states, and nothing it cannot support. */
class CourseAnalyticsPageRenderTest {

    private static String render(Map<String, Object> model) {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var servlet = new MockServletContext();
        var applicationContext = new StaticWebApplicationContext();
        applicationContext.setServletContext(servlet);
        applicationContext.refresh();
        servlet.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, applicationContext);
        var request = new MockHttpServletRequest(servlet);
        var response = new MockHttpServletResponse();
        var context = new WebContext(JakartaServletWebApplication.buildApplication(servlet).buildExchange(request, response));
        Map<String, Object> variables = new HashMap<>(model);
        variables.put("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "fixture"));
        context.setVariables(variables);
        return engine.process("course_analytics", context);
    }

    private static StudentResult student(long id, String name, double mark, boolean evaluated, Map<Integer, Double> clo) {
        return new StudentResult(id, name, "Team A", mark, evaluated, false, 1.0, clo);
    }

    private static Map<String, Object> model(List<StudentResult> results, List<CriterionSample> samples, boolean withAssessment) {
        Assessment a = new Assessment();
        a.setId(1L);
        a.setTitle("Final presentation");
        Map<String, Object> m = new HashMap<>();
        m.put("summary", CohortMetrics.summarise(results, 50, mark -> mark >= 50 ? "C" : "F"));
        m.put("cloRows", CohortMetrics.cloRows(results, Map.of(1, 20.0), 50));
        m.put("criterionRows", CohortMetrics.criterionRows(samples));
        m.put("attention", CohortMetrics.attention(results, 50));
        m.put("passMark", 50.0);
        m.put("cloTarget", 50.0);
        m.put("assessments", withAssessment ? List.of(a) : List.of());
        m.put("allAssessments", withAssessment ? List.of(a) : List.of());
        m.put("groups", List.of());
        m.put("selectedGroupId", null);
        m.put("selectedAssessmentIds", List.of());
        m.put("adminUsername", "admin1");
        return m;
    }

    @Test
    void aCourseWithoutAssessmentsSaysSoInsteadOfShowingEmptyCards() {
        String html = render(model(List.of(), List.of(), false));

        assertTrue(html.contains("This course has no assessments yet"));
        assertFalse(html.contains("Average mark"));
    }

    @Test
    void whenNobodyIsEvaluatedTheCardsSayNoDataAndNoNumbersAreInvented() {
        String html = render(model(List.of(student(1, "Not marked", 0, false, Map.of())), List.of(), true));

        assertTrue(html.contains("Nothing has been evaluated yet"));
        assertTrue(html.contains("No data yet"));
        assertFalse(html.contains("Grade distribution"));
    }

    @Test
    void anEvaluatedClassShowsTheCoreCardsOutcomesCriteriaAndWhoNeedsAWord() {
        List<StudentResult> results = List.of(
                student(1, "Strong", 85, true, Map.of(1, 18.0)), student(2, "Near", 52, true, Map.of(1, 9.0)),
                student(3, "Fail", 30, true, Map.of(1, 4.0)), student(4, "Solid", 70, true, Map.of(1, 14.0)),
                student(5, "Also solid", 75, true, Map.of(1, 15.0)));
        List<CriterionSample> samples = java.util.stream.IntStream.range(0, 6)
                .mapToObj(i -> new CriterionSample("k", "Report - Clarity", 0.3, false, true)).toList();

        String html = render(model(results, samples, true));

        assertTrue(html.contains("Average mark"));
        assertTrue(html.contains("Pass rate"));
        assertTrue(html.contains("Learning outcomes (CLO)"));
        assertTrue(html.contains("CLO <span>1</span>") || html.contains(">CLO <"));
        assertTrue(html.contains("Report - Clarity"));
        assertTrue(html.contains("Hard"));
        assertTrue(html.contains("Below the pass mark"));
        assertTrue(html.contains("close to the pass mark"));
        assertTrue(html.contains("/admin/analytics/lecturer-comparison"));
    }

    @Test
    void withFewStudentsTheSpreadIsNotDescribedInWords() {
        String html = render(model(List.of(student(1, "A", 80, true, Map.of()), student(2, "B", 60, true, Map.of())), List.of(), true));

        assertTrue(html.contains("too few to describe how marks are spread"));
    }

    @Test
    void namesAreShownAsTextNotMarkup() {
        String html = render(model(List.of(student(1, "<script>alert('x')</script>", 30, true, Map.of())), List.of(), true));

        assertFalse(html.contains("<script>alert('x')</script>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    @Test
    void theAdminCanSetTheOutcomeTargetFromTheFilterArea() {
        Map<String, Object> m = model(List.of(), List.of(), true);
        m.put("cloTarget", 65.0);
        m.put("selectedGroupId", 3L);
        m.put("selectedAssessmentIds", List.of(1L));

        String html = render(m);

        assertTrue(html.contains("/admin/course-analytics/clo-target"));
        assertTrue(html.contains("name=\"cloTarget\""));
        assertTrue(html.matches("(?s).*<input[^>]*id=\"caCloTarget\"[^>]*value=\"65\".*"), "the current target is in the box");
        assertTrue(html.contains("Save target"));
        assertTrue(html.contains("<input type=\"hidden\" name=\"groupId\" value=\"3\">"), "the chosen team is kept after saving");
        assertTrue(html.contains("<input type=\"hidden\" name=\"assessmentIds\" value=\"1\">"), "and so are the chosen assessments");
        assertTrue(html.contains("name=\"_csrf\""));
    }

    @Test
    void aSavedOrRefusedTargetIsAnnounced() {
        Map<String, Object> ok = model(List.of(), List.of(), true);
        ok.put("successMessage", "Learning outcome target set to 65% for this course.");
        assertTrue(render(ok).contains("Learning outcome target set to 65% for this course."));

        Map<String, Object> refused = model(List.of(), List.of(), true);
        refused.put("errorMessage", "Only the admin who created this course can change its targets.");
        assertTrue(render(refused).contains("Only the admin who created this course can change its targets."));
    }

    @Test
    void theAdminCanDownloadThePageForTheChosenTeamAndAssessments() {
        Map<String, Object> m = model(List.of(), List.of(), true);
        m.put("selectedGroupId", 3L);
        m.put("selectedAssessmentIds", List.of(1L));

        String html = render(m);

        assertTrue(html.contains("/admin/data-views/course-analytics/export"));
        assertTrue(html.matches("(?s).*<button[^>]*name=\"format\"[^>]*value=\"xlsx\"[^>]*>\\s*Excel \\(\\.xlsx\\)\\s*</button>.*"));
        assertTrue(html.matches("(?s).*<button[^>]*name=\"format\"[^>]*value=\"pdf\"[^>]*>\\s*PDF\\s*</button>.*"));
        int exportForm = html.indexOf("class=\"ca-export\"");
        String afterExport = html.substring(exportForm);
        assertTrue(afterExport.contains("<input type=\"hidden\" name=\"groupId\" value=\"3\">"), "the download keeps the chosen team");
        assertTrue(afterExport.contains("<input type=\"hidden\" name=\"assessmentIds\" value=\"1\">"), "and the chosen assessments");
    }
}
