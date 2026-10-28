package com.capstone.adproject.analytics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import com.capstone.adproject.model.*;

class CommentModerationTemplateTest {
    @Test void rendersStudentAndAssessorComments() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/"); resolver.setSuffix(".html");
        var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        var servlet = new MockServletContext();
        var request = new MockHttpServletRequest(servlet);
        request.setRequestURI("/admin/progress-tracking/100");
        var context = new WebContext(JakartaServletWebApplication.buildApplication(servlet)
            .buildExchange(request, new MockHttpServletResponse()));
        var assessment = new Assessment(); assessment.setId(100L); assessment.setTitle("Review");
        var comments = new ArrayList<AssessmentComment>();
        for (var type : List.of(AssessmentComment.EvaluatorType.STUDENT, AssessmentComment.EvaluatorType.LECTURER)) {
            var c = new AssessmentComment(); c.setId((long) comments.size()+1);
            c.setAssessment(assessment); c.setEvaluatorType(type); c.setEvaluatorName("Author");
            c.setAssessmentType(type == AssessmentComment.EvaluatorType.STUDENT
                ? AssessmentComment.CommentAssessmentType.PEER : AssessmentComment.CommentAssessmentType.LECTURER_EVALUATION);
            c.setCommentText(type + " feedback fixture"); comments.add(c);
        }
        context.setVariables(Map.of("assessment", assessment, "activeTab", "comments",
            "moderationComments", comments, "moderationStats", Map.of("total",2L,"pending",2L,"approved",0L,"rejected",0L),
            "_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "fixture"),
            "currentFilter", "all", "currentRoleFilter", "all", "currentSearch", "",
            "managedCourses", List.of()));
        String page = engine.process("admin_progress_tracking_view", context);
        assertTrue(page.contains("STUDENT feedback fixture"));
        assertTrue(page.contains("LECTURER feedback fixture"));
    }
    @Test void groupViewsRenderCommentsIndexedByDatabaseStudentIds() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/"); resolver.setSuffix(".html");
        var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        var servlet = new MockServletContext();
        var request = new MockHttpServletRequest(servlet);
        request.setRequestURI("/admin/comments/view/assessment/1/group/20");
        var context = new WebContext(JakartaServletWebApplication.buildApplication(servlet)
            .buildExchange(request, new MockHttpServletResponse()));
        var assessment = new Assessment(); assessment.setId(1L); assessment.setTitle("Review");
        var student = new Student(); student.setId(1000L); student.setUsername("Recipient");
        var group = new Group(); group.setId(20L); group.setGroupName("Team");
        group.setStudents(new ArrayList<>(List.of(student)));
        Map<String, Map<String, List<String>>> comments = new LinkedHashMap<>();
        comments.put("Question", Map.of("Peer", List.of("Student feedback fixture"),
            "Assessor", List.of("Assessor feedback fixture")));
        context.setVariables(Map.of("assessment", assessment, "group", group,
            "students", List.of(student), "role", "admin", "isAdmin", true,
            "allCommentsMap", Map.of(student.getId(), comments), "managedCourses", List.of()));
        String page = engine.process("comment_view", context);
        assertTrue(page.contains("Student feedback fixture"));
        assertTrue(page.contains("Assessor feedback fixture"));
        assertFalse(page.contains("No comments received yet."));
        context.setVariable("role", "lecturer");
        context.setVariable("isAdmin", false);
        context.setVariable("commentsByGroupAndStudent",
            Map.of(group, Map.of(student, Map.of("allComments", comments))));
        page = engine.process("lecturer_comment_view", context);
        assertTrue(page.contains("Student feedback fixture"));
        assertTrue(page.contains("Assessor feedback fixture"));
        assertFalse(page.contains("No comments received yet."));
    }

}
