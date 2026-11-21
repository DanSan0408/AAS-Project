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
import org.springframework.web.servlet.support.RequestContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.context.webmvc.SpringWebMvcThymeleafRequestContext;
import org.thymeleaf.spring6.naming.SpringContextVariableNames;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Student;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Renders the Manage Students / Manage Assessors pages to check the "no confirmed welcome email" panel. */
class ManageUsersWelcomeEmailTemplateTest {

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
        request.setRequestURI("/admin/" + template.replace('_', '-'));
        var response = new MockHttpServletResponse();
        var context = new WebContext(JakartaServletWebApplication.buildApplication(servlet).buildExchange(request, response));

        Map<String, Object> variables = new HashMap<>(model);
        variables.put("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "fixture"));
        variables.put("managedCourses", List.of());
        variables.put("adminUsername", "admin@example.com");
        // th:field needs the request contexts that ThymeleafView normally provides.
        var requestContext = new RequestContext(request, response, servlet, variables);
        variables.put(SpringContextVariableNames.SPRING_REQUEST_CONTEXT, requestContext);
        variables.put(SpringContextVariableNames.THYMELEAF_REQUEST_CONTEXT,
            new SpringWebMvcThymeleafRequestContext(requestContext, request));
        context.setVariables(variables);
        return engine.process(template, context);
    }

    private static Student student(Long id, String email) {
        Student student = new Student();
        student.setId(id);
        student.setEmail(email);
        student.setUsername(email.split("@")[0]);
        return student;
    }

    @Test
    void manageStudentsListsAccountsWithNoConfirmedWelcomeEmail() {
        Student pending = student(7L, "never.emailed@example.com");
        String page = render("manage_students", Map.of(
            "student", new Student(),
            "students", List.of(pending, student(8L, "fine@example.com")),
            "pendingWelcomeEmails", List.of(pending)));

        assertTrue(page.contains("1 student account(s) with no confirmed welcome email"));
        assertTrue(page.contains("action=\"/admin/welcome-emails/resend\""));
        assertTrue(page.contains("name=\"audience\" value=\"students\""));
        assertTrue(page.contains("name=\"id\" value=\"7\""));
        assertTrue(page.contains("Send welcome email to never.emailed@example.com"));
    }

    @Test
    void manageStudentsHidesPanelWhenEveryWelcomeEmailIsConfirmed() {
        String page = render("manage_students", Map.of(
            "student", new Student(),
            "students", List.of(student(8L, "fine@example.com")),
            "pendingWelcomeEmails", List.of()));

        assertFalse(page.contains("no confirmed welcome email"));
        assertFalse(page.contains("/admin/welcome-emails/resend"));
    }

    @Test
    void manageLecturersListsAccountsWithNoConfirmedWelcomeEmail() {
        Lecturer pending = new Lecturer();
        pending.setId(3L);
        pending.setEmail("assessor@example.com");
        pending.setUsername("assessor");

        String page = render("manage_lecturers", Map.of(
            "lecturer", new Lecturer(),
            "lecturers", List.of(pending),
            "pendingWelcomeEmails", List.of(pending)));

        assertTrue(page.contains("1 assessor account(s) with no confirmed welcome email"));
        assertTrue(page.contains("name=\"audience\" value=\"lecturers\""));
        assertTrue(page.contains("name=\"id\" value=\"3\""));
    }

    @Test
    void manageStudentsShowsAWelcomeEmailButtonBesideUpdateAndABulkButton() {
        Student unactivated = student(7L, "new.student@example.com");
        Student activated = student(8L, "active.student@example.com");
        String page = render("manage_students", Map.of(
            "student", new Student(),
            "students", List.of(unactivated, activated),
            "pendingWelcomeEmails", List.of(),
            "unactivatedIds", java.util.Set.of(7L)));

        assertTrue(page.contains("1</strong>"), "bulk bar shows how many accounts still have to set a password");
        assertTrue(page.contains("name=\"scope\" value=\"all\""), "bulk button targets everyone who has not set a password");
        assertTrue(page.contains("Send welcome email to new.student@example.com"), "row button for the unactivated account");
        assertFalse(page.contains("Send welcome email to active.student@example.com"), "no live button for an activated account");
        assertTrue(page.contains("This account has already set its password"), "activated account shows a disabled button");
    }

    @Test
    void manageLecturersShowsAWelcomeEmailButtonBesideUpdate() {
        Lecturer lecturer = new Lecturer();
        lecturer.setId(3L);
        lecturer.setEmail("assessor@example.com");
        lecturer.setUsername("assessor");
        String page = render("manage_lecturers", Map.of(
            "lecturer", new Lecturer(),
            "lecturers", List.of(lecturer),
            "pendingWelcomeEmails", List.of(),
            "unactivatedIds", java.util.Set.of(3L)));

        assertTrue(page.contains("Send welcome email to assessor@example.com"));
        assertTrue(page.contains("name=\"audience\" value=\"lecturers\""));
    }
}
