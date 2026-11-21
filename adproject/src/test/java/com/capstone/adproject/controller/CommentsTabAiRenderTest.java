package com.capstone.adproject.controller;

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
import com.capstone.adproject.model.AssessmentComment;
import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.service.AiAgreement;
import com.capstone.adproject.service.CommentScanScheduler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The comments tab shows what the AI check found, as advice, and offers the scan and the safe approval. */
class CommentsTabAiRenderTest {

    private static String render(Map<String, Object> model, String template) {
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
        return engine.process(template, context);
    }

    private static AssessmentComment comment(long id, String text, String aiLabel, Double confidence, String reason) {
        Student target = new Student();
        target.setId(9L);
        target.setUsername("Chen");
        AssessmentComment c = new AssessmentComment();
        c.setId(id);
        c.setCommentText(text);
        c.setEvaluatedStudent(target);
        c.setApprovalStatus(AssessmentComment.ApprovalStatus.PENDING);
        c.setEvaluatorType(AssessmentComment.EvaluatorType.STUDENT);
        c.setAssessmentType(AssessmentComment.CommentAssessmentType.PEER);
        c.setAiLabel(aiLabel);
        c.setAiConfidence(confidence);
        c.setAiReason(reason);
        return c;
    }

    private static Map<String, Object> commentsModel(List<AssessmentComment> comments, long notScanned, long flagged) {
        Course course = new Course();
        course.setId(10L);
        course.setCourseCode("SWE4949");
        course.setCourseName("Capstone");
        Assessment assessment = new Assessment();
        assessment.setId(100L);
        assessment.setTitle("Final presentation");
        assessment.setCourse(course);
        Map<String, Long> stats = new HashMap<>();
        stats.put("total", (long) comments.size());
        stats.put("pending", (long) comments.size());
        stats.put("approved", 0L);
        stats.put("rejected", 0L);
        stats.put("flagged", flagged);
        stats.put("notScanned", notScanned);
        Map<String, Object> m = new HashMap<>();
        m.put("assessment", assessment);
        m.put("activeTab", "comments");
        m.put("adminUsername", "admin1");
        m.put("moderationStats", stats);
        m.put("moderationComments", comments);
        m.put("courseGroups", List.of());
        m.put("currentFilter", "all");
        m.put("currentRoleFilter", "all");
        m.put("currentSearch", "");
        m.put("currentGroupId", null);
        return m;
    }

    @Test
    void theScanAndSafeApprovalControlsAreOfferedAndPostToTheirEndpoints() {
        String html = render(commentsModel(List.of(comment(1, "Good work", null, null, null)), 1, 0), "admin_progress_tracking_view");

        assertTrue(html.contains("/admin/comments/assessment/100/ai-scan"));
        assertTrue(html.contains("/admin/comments/assessment/100/bulk-approve-safe"));
        assertTrue(html.contains("Scan pending with AI"));
        assertTrue(html.contains("filter=flagged"));
    }

    @Test
    void theScanButtonIsDisabledWhenEveryPendingCommentHasBeenRead() {
        String html = render(commentsModel(List.of(comment(1, "Good work", "ok", 0.95, "Constructive")), 0, 0), "admin_progress_tracking_view");

        assertTrue(html.matches("(?s).*<button[^>]*id=\"btnAiScan\"[^>]*disabled.*"));
    }

    @Test
    void aFlaggedCommentShowsTheFindingItsConfidenceAndReason() {
        String html = render(commentsModel(List.of(comment(1, "You are useless", "abusive", 0.9, "Insults a teammate")), 0, 1), "admin_progress_tracking_view");

        assertTrue(html.contains("Possible insult or abuse"));
        assertTrue(html.contains("90% sure"));
        assertTrue(html.contains("Insults a teammate"));
        assertTrue(html.contains("ai-flag--review"));
    }

    @Test
    void aCommentTheAiHasNotReadShowsNoFinding() {
        String html = render(commentsModel(List.of(comment(1, "Good work", null, null, null)), 1, 0), "admin_progress_tracking_view");

        assertFalse(html.contains("class=\"ai-flag"));
    }

    @Test
    void theAiReasonIsShownAsTextNotAsMarkup() {
        String html = render(commentsModel(List.of(comment(1, "x", "abusive", 0.9, "<script>alert('x')</script>")), 0, 1), "admin_progress_tracking_view");

        assertFalse(html.contains("<script>alert('x')</script>"));
    }

    // --- the super admin page ----------------------------------------------------------------------------------

    private static Map<String, Object> superAdminModel(AiAgreement.Report report, CommentScanScheduler.RunSummary lastRun) {
        Map<String, Object> m = new HashMap<>();
        m.put("report", report);
        m.put("pendingRead", 3L);
        m.put("pendingUnread", 2L);
        m.put("scanEnabled", true);
        m.put("lastRun", lastRun);
        return m;
    }

    @Test
    void theSuperAdminPageSaysSoWhenThereIsNothingToCompare() {
        String html = render(superAdminModel(AiAgreement.compare(List.of()), null), "superadmin_ai_comments");

        assertTrue(html.contains("Nothing to compare yet"));
        assertTrue(html.contains("No scan has run since the application started."));
        assertTrue(html.contains("/superadmin/ai-comments/scan-now"));
    }

    @Test
    void theSuperAdminPageShowsAgreementAndTheLastRun() {
        AiAgreement.Report report = new AiAgreement.Report(10, 8, 1, 1, 4, 4, 2, 1,
                List.of(new AiAgreement.LabelRow("abusive", "Insult or abuse", 1, 4)));
        var run = new CommentScanScheduler.RunSummary(java.time.Instant.parse("2026-11-21T10:00:00Z"), false, 2, 30, 4, 0, 0, "OK", "");

        String html = render(superAdminModel(report, run), "superadmin_ai_comments");

        assertTrue(html.contains("8 (80%)"));
        assertTrue(html.contains("Insult or abuse"));
        assertTrue(html.contains("decisions so far"));
        assertTrue(html.contains("approved in bulk"));
    }
}
