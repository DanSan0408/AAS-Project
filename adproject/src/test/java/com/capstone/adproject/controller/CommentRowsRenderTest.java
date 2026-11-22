package com.capstone.adproject.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Student;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The comment pages show every comment as one horizontal row: who, what it is about, the text, and when. */
class CommentRowsRenderTest {

    private static String render(String template, Map<String, Object> model) {
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

    private static long rows(String html) {
        Matcher m = Pattern.compile("<li[^>]*class=\"comment-row\"").matcher(html);
        long count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }

    private static AssessmentComment comment(String displayName, String text, String label, Long rubricId, String anonymousId) {
        AssessmentComment c = new AssessmentComment();
        c.setDisplayName(displayName);
        c.setCommentText(text);
        c.setCommentLabel(label);
        c.setRubricId(rubricId);
        c.setAnonymousIdentifier(anonymousId);
        c.setSubmittedAt(LocalDateTime.of(2026, 11, 5, 14, 30));
        return c;
    }

    // --- student ----------------------------------------------------------------------------------------------------

    private static Map<String, Object> studentModel(Map<String, Map<String, List<AssessmentComment>>> byAssessment) {
        Map<String, Object> model = new HashMap<>();
        model.put("commentsByAssessment", byAssessment);
        model.put("totalComments", 5);
        model.put("peerCommentCount", 2);
        model.put("lecturerCommentCount", 1);
        model.put("supervisorCommentCount", 1);
        return model;
    }

    private static Map<String, Map<String, List<AssessmentComment>>> oneAssessment() {
        Map<String, List<AssessmentComment>> kinds = new LinkedHashMap<>();
        kinds.put("self", List.of(comment("You", "I led the demo.", "Reflection", null, null)));
        kinds.put("peer", List.of(
                comment("Teammate 1", "Always on time.", null, 7L, "Teammate 1"),
                comment("Aiman", "Helped me debug.", "Teamwork", null, null)));
        kinds.put("lecturer", List.of(comment("Lecturer", "Clear structure.", null, null, null)));
        kinds.put("supervisor", List.of(comment("Mr. Ahmad", "Good initiative.", null, null, null)));
        Map<String, Map<String, List<AssessmentComment>>> byAssessment = new LinkedHashMap<>();
        byAssessment.put("Final presentation", kinds);
        return byAssessment;
    }

    @Test
    void theStudentPageListsEveryCommentAsARowInOrderSelfPeerAssessorSupervisor() {
        String html = render("student_comments", studentModel(oneAssessment()));

        assertEquals(5, rows(html));
        assertTrue(html.contains("class=\"comment-rows\""));
        assertFalse(html.contains("comments-grid"), "the card grid is gone");
        assertFalse(html.contains("comment-card"), "and so are the cards");
        int self = html.indexOf("I led the demo."), peer = html.indexOf("Always on time."),
                assessor = html.indexOf("Clear structure."), supervisor = html.indexOf("Good initiative.");
        assertTrue(self > 0 && self < peer && peer < assessor && assessor < supervisor, "rows follow the fixed order");
    }

    @Test
    void eachStudentRowSaysWhoItIsFromWhatItIsAboutAndWhen() {
        String html = render("student_comments", studentModel(oneAssessment()));

        assertTrue(html.contains("data-kind=\"self\"") && html.contains("data-kind=\"peer\"")
                && html.contains("data-kind=\"lecturer\"") && html.contains("data-kind=\"supervisor\""));
        assertTrue(html.contains("You (Self)"));
        assertTrue(html.contains("Aiman"));
        assertTrue(html.contains("Teamwork"), "the label is the topic");
        assertTrue(html.contains("Rubric comment"), "a rubric comment without a label says so");
        assertTrue(html.contains("General"), "and a general one says so");
        assertTrue(html.contains("05 Nov 2026, 14:30"));
    }

    @Test
    void anonymousAuthorsAreTaggedAndTheAssessorIsNeverNamedLecturer() {
        String html = render("student_comments", studentModel(oneAssessment()));

        assertEquals(2, Pattern.compile("comment-row-tag").matcher(html).results().count(),
                "the anonymous teammate and the anonymous assessor");
        assertTrue(html.contains(">Assessor<"));
        assertFalse(html.contains(">Lecturer<"));
    }

    @Test
    void aCommentIsEscapedNotInterpreted() {
        Map<String, Map<String, List<AssessmentComment>>> byAssessment = new LinkedHashMap<>();
        byAssessment.put("A", Map.of("peer", List.of(comment("Eve", "<script>alert(1)</script>", null, null, null))));

        String html = render("student_comments", studentModel(byAssessment));

        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertFalse(html.contains("<script>alert(1)"));
    }

    @Test
    void theStudentPageCanBeFilteredByWhoWroteTheComment() {
        String html = render("student_comments", studentModel(oneAssessment()));

        for (String label : List.of(">All<", ">You<", ">Peers<", ">Assessors<", ">Supervisors<")) {
            assertTrue(html.contains(label), label);
        }
        assertTrue(html.contains("data-kinds=\"self team\""));
        assertTrue(html.contains("js/comment-rows.js") && html.contains("css/comment_rows.css"));
    }

    @Test
    void withNoCommentsTheStudentSeesTheEmptyStateAndNoRows() {
        String html = render("student_comments", studentModel(new LinkedHashMap<>()));

        assertEquals(0, rows(html));
        assertTrue(html.contains("No Comments Available"));
        assertFalse(html.contains("comment-filter-btn"), "nothing to filter");
    }

    // --- admin and lecturer -----------------------------------------------------------------------------------------

    private static Map<String, Map<String, List<String>>> topics() {
        Map<String, List<String>> reliability = new LinkedHashMap<>();
        reliability.put("Teammate 2", List.of("Reliable.", "Meets deadlines."));
        reliability.put("Dr. Hakim", List.of("Consistent effort."));
        Map<String, List<String>> teamwork = new LinkedHashMap<>();
        teamwork.put("Teammate 3", List.of("Listens well."));
        Map<String, Map<String, List<String>>> byTopic = new LinkedHashMap<>();
        byTopic.put("How reliable is this member?", reliability);
        byTopic.put("How well do they work in a team?", teamwork);
        return byTopic;
    }

    private static Student student(long id, String name) {
        Student s = new Student();
        s.setId(id);
        s.setUsername(name);
        s.setEmail(name.toLowerCase() + "@uni.edu");
        return s;
    }

    private static Map<String, Object> pageModel() {
        Assessment assessment = new Assessment();
        assessment.setId(5L);
        assessment.setTitle("Final presentation");
        Group group = new Group();
        group.setId(3L);
        group.setGroupName("Team Alpha");
        Map<String, Object> model = new HashMap<>();
        model.put("assessment", assessment);
        model.put("group", group);
        model.put("role", "admin");
        return model;
    }

    @Test
    void theAdminPageShowsEachStudentsCommentsGroupedByTopicAsRows() {
        Map<String, Object> model = pageModel();
        model.put("students", List.of(student(11L, "Chen")));
        model.put("allCommentsMap", Map.of(11L, topics()));

        String html = render("comment_view", model);

        assertEquals(4, rows(html));
        assertTrue(html.contains("How reliable is this member?") && html.contains("How well do they work in a team?"));
        assertTrue(html.contains("Reliable.") && html.contains("Consistent effort.") && html.contains("Listens well."));
        assertTrue(html.contains("Dr. Hakim"));
        assertTrue(Pattern.compile("comment-topic-count\">3<").matcher(html).find(), "the topic shows how many comments it has");
        assertTrue(Pattern.compile("comment-topic-count\">1<").matcher(html).find());
        assertFalse(html.contains("'- &quot;'"), "the old dash-and-quote lines are gone");
    }

    @Test
    void aStudentWithNoCommentsSaysSoInsteadOfShowingAnEmptyList() {
        Map<String, Object> model = pageModel();
        model.put("students", List.of(student(11L, "Chen")));
        model.put("allCommentsMap", Map.of(11L, new LinkedHashMap<String, Object>()));

        String html = render("comment_view", model);

        assertEquals(0, rows(html));
        assertTrue(html.contains("No comments received yet."));
    }

    @Test
    void theLecturerPageUsesTheSameRows() {
        Map<String, Object> model = pageModel();
        model.put("role", "lecturer");
        Map<Group, Map<Student, Map<String, Object>>> byGroup = new LinkedHashMap<>();
        Map<Student, Map<String, Object>> byStudent = new LinkedHashMap<>();
        Map<String, Object> studentComments = new HashMap<>();
        studentComments.put("allComments", topics());
        byStudent.put(student(11L, "Chen"), studentComments);
        byGroup.put((Group) model.get("group"), byStudent);
        model.put("commentsByGroupAndStudent", byGroup);

        String html = render("lecturer_comment_view", model);

        assertEquals(4, rows(html));
        assertTrue(html.contains("Meets deadlines."));
        assertTrue(html.contains("js/comment-rows.js") && html.contains("css/comment_rows.css"));
    }

    @Test
    void aCommentWithMarkupIsEscapedOnTheAdminPageToo() {
        Map<String, Object> model = pageModel();
        model.put("students", List.of(student(11L, "Chen")));
        Map<String, List<String>> evaluators = new LinkedHashMap<>();
        evaluators.put("<b>Mallory</b>", List.of("<img src=x onerror=alert(1)>"));
        model.put("allCommentsMap", Map.of(11L, Map.of("Topic", evaluators)));

        String html = render("comment_view", model);

        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"));
        assertTrue(html.contains("&lt;b&gt;Mallory&lt;/b&gt;"));
        assertFalse(html.contains("<img src=x"));
    }
}
