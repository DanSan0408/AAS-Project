package com.capstone.adproject.analytics;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.jpa.repository.Query;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import com.capstone.adproject.dto.analytics.LecturerComparisonData.Option;
import com.capstone.adproject.model.Mark;
import com.capstone.adproject.repositories.MarkRepository;

import jakarta.persistence.Entity;

class LecturerAnalyticsInfrastructureTest {
    @Test void analyticsQueryParsesAgainstTheRealEntityMappingsWithoutConnectingToADatabase() throws Exception {
        Configuration config = new Configuration();
        config.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        config.setProperty("hibernate.boot.allow_jdbc_metadata_access", "false");
        config.setProperty("hibernate.hbm2ddl.auto", "none");
        var resources = new PathMatchingResourcePatternResolver().getResources("classpath*:com/capstone/adproject/model/*.class");
        for (var resource : resources) {
            String name = resource.getFilename();
            if (name == null || name.contains("$")) continue;
            Class<?> type = Class.forName("com.capstone.adproject.model." + name.substring(0, name.length() - 6));
            if (type.isAnnotationPresent(Entity.class)) config.addAnnotatedClass(type);
        }
        var method = MarkRepository.class.getMethod("findLecturerAnalyticsMarks", Long.class, Long.class, List.class);
        String hql = method.getAnnotation(Query.class).value();
        try (var factory = config.buildSessionFactory(); var session = factory.openSession()) {
            var query = session.createQuery(hql, Object[].class);
            query.setParameter("assessmentId", 20L);
            query.setParameter("courseId", 10L);
            query.setParameter("statuses", List.of(Mark.SubmissionStatus.FINAL, Mark.SubmissionStatus.SUBMITTED));
            assertNotNull(query);
        }
    }
    @Test void analyticsPageAndSharedFragmentsRenderWithRecordOptions() throws Exception {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setTemplateMode("HTML");
        var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        var servlet = new MockServletContext();
        var request = new MockHttpServletRequest(servlet); request.setRequestURI("/admin/analytics/lecturer-comparison");
        var response = new MockHttpServletResponse();
        var web = JakartaServletWebApplication.buildApplication(servlet);
        var context = new WebContext(web.buildExchange(request, response));
        context.setVariable("analyticsAssessments", List.of(new Option("20", "Review <script>alert(1)</script>")));
        context.setVariable("selectedAssessmentId", "20");
        context.setVariable("managedCourses", List.of());
        context.setVariable("activeCourse", null);
        String page = engine.process("lecturer_comparison_analytics", context);
        assertTrue(page.contains("lecturer-analytics.js"));
        assertTrue(page.contains("value=\"20\" selected=\"selected\""));
        assertTrue(page.contains("Review &lt;script&gt;"));
        assertTrue(page.contains("id=\"analytics-breakdown\""));
        assertFalse(page.contains("th:replace="));
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/analytics-rendered-fixture.html"), page);
    }
    @Test void authenticationPagesRenderWithoutBreadcrumbsAndKeepTheirForms() throws Exception {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setTemplateMode("HTML");
        var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        var servlet = new MockServletContext();
        for (String name : List.of("login", "forgot_password_form", "reset_password_form")) {
            var request = new MockHttpServletRequest(servlet); request.setRequestURI("/login");
            var context = new WebContext(JakartaServletWebApplication.buildApplication(servlet)
                .buildExchange(request, new MockHttpServletResponse()));
            context.setVariable("token", "fixture-token");
            String page = engine.process(name, context);
            assertFalse(page.contains("system-breadcrumb"), name);
            assertTrue(page.contains("IAMS_logo2.png"), name);
            assertTrue(page.contains("method=\"post\""), name);
            java.nio.file.Files.writeString(java.nio.file.Path.of("target/" + name + "-rendered-fixture.html"), page);
        }
    }
}
