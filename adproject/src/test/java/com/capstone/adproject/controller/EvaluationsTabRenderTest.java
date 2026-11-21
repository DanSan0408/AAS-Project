package com.capstone.adproject.controller;

import java.time.LocalDateTime;
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
import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.EvaluationDeletionLog;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.service.ProgressTrackingService.EvaluationCommentView;
import com.capstone.adproject.service.ProgressTrackingService.EvaluationLine;
import com.capstone.adproject.service.ProgressTrackingService.EvaluationRow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Finished Evaluations tab: pick first, then read (dropdown) and delete. There is no edit control. */
class EvaluationsTabRenderTest {

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
        return engine.process("admin_progress_tracking_view", context);
    }

    private static Map<String, Object> pageModel(boolean picked, List<EvaluationRow> rows) {
        Course course = new Course();
        course.setId(10L);
        course.setCourseCode("SWE4949");
        course.setCourseName("Capstone");
        Assessment assessment = new Assessment();
        assessment.setId(100L);
        assessment.setTitle("Final presentation");
        assessment.setCourse(course);

        Group group = new Group();
        group.setId(3L);
        group.setGroupName("Team A");
        Student student = new Student();
        student.setId(103L);
        student.setUsername("Chen");
        Rubric rubric = new Rubric();
        rubric.setId(12L);
        rubric.setName("Report");

        Map<String, Object> m = new HashMap<>();
        m.put("assessment", assessment);
        m.put("activeTab", "evaluations");
        m.put("adminUsername", "admin1");
        m.put("moderationStats", Map.of("pending", 0L));
        m.put("courseAssessments", List.of(assessment));
        m.put("courseGroups", List.of(group));
        m.put("evaluationStudents", List.of(student));
        m.put("evaluationRubrics", List.of(rubric));
        m.put("evalGroupId", null);
        m.put("evalStudentId", picked ? 103L : null);
        m.put("evalRubricId", null);
        m.put("evaluationsPicked", picked);
        m.put("evaluations", rows);
        m.put("recentDeletions", List.of());
        return m;
    }

    private static EvaluationRow row(String evaluator, String target, String commentText) {
        return new EvaluationRow("LECTURER", 5L, evaluator, target, List.of(103L), 12L, "Report", "Individual Assessment",
                LocalDateTime.of(2026, 11, 19, 10, 30), 15.0, 20.0,
                List.of(new EvaluationLine("Clarity", "Good", 15.0, 20.0)),
                List.of(new EvaluationCommentView("Strengths", commentText)));
    }

    @Test
    void beforeAnythingIsPickedItAsksToPickAndListsNothing() {
        String html = render(pageModel(false, List.of()));

        assertTrue(html.contains("Choose a group, a student or a rubric above"));
        assertFalse(html.contains("eval-delete"));
        assertTrue(html.contains("name=\"assessmentId\""));
        assertTrue(html.contains("name=\"evalGroupId\""));
        assertTrue(html.contains("name=\"evalStudentId\""));
        assertTrue(html.contains("name=\"evalRubricId\""));
    }

    @Test
    void anEmptyResultSaysSoInsteadOfShowingABlankList() {
        String html = render(pageModel(true, List.of()));

        assertTrue(html.contains("No finished evaluations match your choice."));
    }

    @Test
    void eachEvaluationIsADropdownWithItsSummaryAndADeleteForm() {
        String html = render(pageModel(true, List.of(row("Dr Lim", "Chen", "Clear structure"))));

        assertTrue(html.contains("<details class=\"eval-item\""));
        assertTrue(html.contains("Dr Lim"));
        assertTrue(html.contains("Chen"));
        assertTrue(html.contains("Clarity"));
        assertTrue(html.contains("Good"));
        assertTrue(html.contains("15.00"));
        assertTrue(html.contains("Clear structure"));
        assertTrue(html.contains("/admin/progress-tracking/100/evaluations/delete"));
        assertTrue(html.contains("name=\"evaluatorKind\" value=\"LECTURER\""));
        assertTrue(html.contains("name=\"targetIds\" value=\"103\""));
        assertTrue(html.contains("name=\"evalStudentId\" value=\"103\""));
        assertTrue(html.contains("name=\"reason\""));
        assertTrue(html.contains("data-action=\"confirm-submit\""));
        assertEquals(1, html.split("evaluations/delete", -1).length - 1);
    }

    @Test
    void thereIsNoWayToEditAnEvaluation() {
        String html = render(pageModel(true, List.of(row("Dr Lim", "Chen", "Fine"))));

        assertFalse(html.contains("evaluations/edit"));
        assertFalse(html.contains("evaluations/update"));
        assertFalse(html.contains("Edit evaluation"));
    }

    @Test
    void namesAndCommentsAreShownAsTextNotAsMarkup() {
        String html = render(pageModel(true, List.of(row("<b>Evil</b>", "Chen", "<script>alert('x')</script>"))));

        assertFalse(html.contains("<script>alert('x')</script>"));
        assertTrue(html.contains("&lt;script&gt;alert("));
        assertFalse(html.contains("<b>Evil</b>"));
    }

    @Test
    void theDeletionRecordIsShownWhenThereIsOne() {
        EvaluationDeletionLog log = new EvaluationDeletionLog();
        log.setDeletedBy("admin1");
        log.setEvaluatorName("Dr Lim");
        log.setEvaluatedNames("Chen");
        log.setRubricName("Report");
        log.setReason("Entered against the wrong student");
        Map<String, Object> model = pageModel(true, List.of());
        model.put("recentDeletions", List.of(log));

        String html = render(model);

        assertTrue(html.contains("Recently deleted evaluations"));
        assertTrue(html.contains("Entered against the wrong student"));
    }
}
