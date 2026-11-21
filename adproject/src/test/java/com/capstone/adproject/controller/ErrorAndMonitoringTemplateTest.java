package com.capstone.adproject.controller;

import java.time.Instant;
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

import com.capstone.adproject.config.LegacyColumnCleanupMigrationRunner.SchemaFinding;
import com.capstone.adproject.service.MonitoringService.Check;
import com.capstone.adproject.service.MonitoringService.ErrorEvent;
import com.capstone.adproject.service.MonitoringService.Level;
import com.capstone.adproject.service.MonitoringService.Snapshot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Renders the error page and the Super Admin monitoring page; templates are otherwise never exercised by tests. */
class ErrorAndMonitoringTemplateTest {

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

    @Test
    void serverErrorShowsItsOwnTitleMessageAndReference() {
        String page = render("error", Map.of("status", 500, "errorTitle", "Something went wrong on our side",
                "errorMessage", "Please try again.", "errorId", "AB12CD34"));

        assertTrue(page.contains("Error 500"));
        assertTrue(page.contains("Something went wrong on our side"));
        assertTrue(page.contains("AB12CD34"));
        assertTrue(page.contains("Try again"));
        assertFalse(page.contains("System Error"), "the old hard-coded heading must be gone");
    }

    @Test
    void notFoundShowsNoReferenceAndOffersGoingBack() {
        String page = render("error", Map.of("status", 404, "errorTitle", "Page not found",
                "errorMessage", "It does not exist.", "errorId", "AB12CD34"));

        assertTrue(page.contains("Error 404"));
        assertTrue(page.contains("Page not found"));
        assertFalse(page.contains("AB12CD34"), "a reference is only useful for server faults");
        assertTrue(page.contains("Go back"));
    }

    @Test
    void unauthenticatedErrorOffersSignIn() {
        String page = render("error", Map.of("status", 401, "errorTitle", "Please sign in", "errorMessage", "Sign in."));

        assertTrue(page.contains("href=\"/login\""));
        assertTrue(page.contains("Sign in"));
    }

    @Test
    void errorPageStillRendersWhenNothingWasSupplied() {
        String page = render("error", Map.of());

        assertTrue(page.contains("Something went wrong"));
    }

    private static Snapshot snapshot(Level overall, List<Check> checks, List<SchemaFinding> findings, List<ErrorEvent> errors) {
        return new Snapshot(Instant.parse("2026-11-19T04:00:00Z"), overall, checks, 3700, 200, 512, 41, 2, 0.31,
                20000, 40000, 3, 12, 1, findings, errors, "21.0.13", "abc1234");
    }

    @Test
    void monitoringPageShowsHealthySystemWithEmptyStates() {
        String page = render("superadmin_monitoring", Map.of(
                "snapshot", snapshot(Level.UP, List.of(new Check("Database", Level.UP, "Responding", 4L)), List.of(), List.of()),
                "telegramConfigured", false, "emailAlertConfigured", true, "schemaCheckedAt", "never",
                "superAdminUsername", "root@example.com"));

        assertTrue(page.contains("All systems operational"));
        assertTrue(page.contains("Database"));
        assertTrue(page.contains("No differences found"));
        assertTrue(page.contains("No server errors since the application started"));
        assertTrue(page.contains("not configured (set TELEGRAM_BOT_TOKEN"));
        assertTrue(page.contains("1h 1m"), "uptime of 3700 seconds");
        assertTrue(page.contains("abc1234"), "running version is shown so a deploy can be confirmed");
    }

    @Test
    void monitoringPageListsOutagesSchemaFindingsAndRecentErrors() {
        String page = render("superadmin_monitoring", Map.of(
                "snapshot", snapshot(Level.DOWN,
                        List.of(new Check("Database", Level.DOWN, "Query failed", null)),
                        List.of(new SchemaFinding("LEGACY_RELAXED", "marks", "old_weight", "made nullable")),
                        List.of(new ErrorEvent(Instant.parse("2026-11-19T03:59:00Z"), "AB12CD34", 500, "POST",
                                "/admin/save-student", "java.lang.IllegalStateException", "kaput"))),
                "telegramConfigured", true, "emailAlertConfigured", true, "schemaCheckedAt", "2026-11-19 04:00:00 UTC",
                "superAdminUsername", "root@example.com"));

        assertTrue(page.contains("Outage: action required"));
        assertTrue(page.contains("Query failed"));
        assertTrue(page.contains("old_weight"));
        assertTrue(page.contains("Fixed"));
        assertTrue(page.contains("AB12CD34"));
        assertTrue(page.contains("IllegalStateException"));
        assertTrue(page.contains("POST /admin/save-student"));
    }
}
