package com.capstone.adproject.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import com.capstone.adproject.config.LegacyColumnCleanupMigrationRunner;
import com.capstone.adproject.model.Announcement;
import com.capstone.adproject.model.Announcement.Audience;
import com.capstone.adproject.model.Announcement.Category;
import com.capstone.adproject.service.AlertingService;
import com.capstone.adproject.service.AnnouncementService;
import com.capstone.adproject.service.MonitoringService;
import com.capstone.adproject.service.SuperAdminService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** The Super Admin announcement manager: controller flow and the page itself. */
class SuperAdminAnnouncementsTest {

    private AnnouncementService announcementService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        announcementService = mock(AnnouncementService.class);
        var controller = new SuperAdminController(mock(SuperAdminService.class), mock(MonitoringService.class),
                mock(AlertingService.class), mock(LegacyColumnCleanupMigrationRunner.class), announcementService,
                mock(com.capstone.adproject.service.AssessmentCommentService.class), mock(com.capstone.adproject.service.CommentScanScheduler.class));
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "root", "n/a", AuthorityUtils.createAuthorityList("ROLE_SUPER_ADMIN")));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static Announcement sample() {
        Announcement a = new Announcement();
        a.setId(4L);
        a.setTitle("Nightly maintenance");
        a.setMessage("Offline 02:00-02:30.");
        a.setCategory(Category.MAINTENANCE);
        a.setAudience(Audience.ALL);
        a.setCreatedBy("root");
        a.setCreatedAt(LocalDateTime.of(2026, 11, 18, 9, 0));
        return a;
    }

    @Test
    void listPageGetsAnEmptyFormAndEveryAnnouncement() throws Exception {
        when(announcementService.findAll()).thenReturn(List.of(sample()));

        mvc.perform(get("/superadmin/announcements"))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin_announcements"))
                .andExpect(model().attributeExists("announcements", "form", "categories", "audiences", "now"));
    }

    @Test
    void editLoadsTheChosenAnnouncementIntoTheForm() throws Exception {
        when(announcementService.findById(4L)).thenReturn(Optional.of(sample()));

        mvc.perform(get("/superadmin/announcements").param("edit", "4"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("form", org.hamcrest.Matchers.hasProperty("title",
                        org.hamcrest.Matchers.is("Nightly maintenance"))));
    }

    @Test
    void publishingSavesWithTheSignedInSuperAdminAndRedirects() throws Exception {
        mvc.perform(post("/superadmin/announcements")
                        .param("title", "New release")
                        .param("message", "Faster pages.")
                        .param("category", "RELEASE")
                        .param("audience", "STUDENTS")
                        .param("active", "true")
                        .param("startsAt", "")
                        .param("endsAt", "2026-12-01T08:30"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/superadmin/announcements"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(announcementService).save(isNull(), eq("New release"), eq("Faster pages."), eq(Category.RELEASE),
                eq(Audience.STUDENTS), isNull(), eq(true), isNull(), eq(LocalDateTime.of(2026, 12, 1, 8, 30)), eq("root"));
    }

    @Test
    void anUnticketedCheckboxMeansSwitchedOff() throws Exception {
        mvc.perform(post("/superadmin/announcements").param("title", "T").param("message", "M"))
                .andExpect(status().is3xxRedirection());

        verify(announcementService).save(isNull(), eq("T"), eq("M"), isNull(), isNull(), isNull(), eq(false), isNull(), isNull(), eq("root"));
    }

    @Test
    void aRejectedAnnouncementShowsTheReasonAndKeepsWhatWasTyped() throws Exception {
        when(announcementService.save(any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Give the announcement a title."));

        mvc.perform(post("/superadmin/announcements").param("title", " ").param("message", "Keep this text"))
                .andExpect(status().isOk())
                .andExpect(view().name("superadmin_announcements"))
                .andExpect(model().attribute("errorMessage", "Give the announcement a title."))
                .andExpect(model().attribute("form", org.hamcrest.Matchers.hasProperty("message",
                        org.hamcrest.Matchers.is("Keep this text"))));
    }

    @Test
    void toggleAndDeleteRedirectBackToTheList() throws Exception {
        when(announcementService.toggle(4L)).thenReturn(false);

        mvc.perform(post("/superadmin/announcements/4/toggle"))
                .andExpect(redirectedUrl("/superadmin/announcements"))
                .andExpect(flash().attribute("successMessage", "Announcement switched off."));
        mvc.perform(post("/superadmin/announcements/4/delete"))
                .andExpect(redirectedUrl("/superadmin/announcements"));

        verify(announcementService).delete(4L);
    }

    // --- page rendering ------------------------------------------------------------------------------------

    private static String renderPage(Map<String, Object> model) {
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
        return engine.process("superadmin_announcements", context);
    }

    private static Map<String, Object> pageModel(List<Announcement> rows, Announcement form) {
        Map<String, Object> m = new HashMap<>();
        m.put("announcements", rows);
        m.put("form", form);
        m.put("startsAtInput", "");
        m.put("endsAtInput", "");
        m.put("categories", Category.values());
        m.put("audiences", Audience.values());
        com.capstone.adproject.model.Course swe = new com.capstone.adproject.model.Course();
        swe.setId(10L);
        swe.setCourseCode("SWE4949");
        swe.setCourseName("Capstone");
        m.put("courses", List.of(swe));
        m.put("courseNames", Map.of(10L, "SWE4949 - Capstone"));
        m.put("now", LocalDateTime.of(2026, 11, 19, 10, 0));
        m.put("serverZone", "UTC");
        return m;
    }

    @Test
    void emptyStateExplainsWhatToDoNext() {
        String page = renderPage(pageModel(List.of(), new Announcement()));

        assertTrue(page.contains("No announcements yet"));
        assertTrue(page.contains("New announcement"));
        assertTrue(page.contains("Publish announcement"));
        assertTrue(page.contains("for=\"annTitle\""), "every field keeps a visible label");
    }

    @Test
    void listShowsStatusTypeAudienceAndRowActions() {
        Announcement scheduled = sample();
        scheduled.setId(5L);
        scheduled.setTitle("Release 2.4");
        scheduled.setCategory(Category.RELEASE);
        scheduled.setAudience(Audience.ASSESSORS);
        scheduled.setStartsAt(LocalDateTime.of(2026, 12, 1, 0, 0));

        String page = renderPage(pageModel(List.of(sample(), scheduled), new Announcement()));

        assertTrue(page.contains("Nightly maintenance"));
        assertTrue(page.contains("Live"));
        assertTrue(page.contains("Scheduled"));
        assertTrue(page.contains("New release"));
        assertTrue(page.contains("Assessors"));
        assertTrue(page.contains("/superadmin/announcements/5/toggle"));
        assertTrue(page.contains("/superadmin/announcements/5/delete"));
        assertFalse(page.contains("No announcements yet"));
    }

    @Test
    void editingShowsSaveChangesAndKeepsTheId() {
        String page = renderPage(pageModel(List.of(sample()), sample()));

        assertTrue(page.contains("Edit announcement"));
        assertTrue(page.contains("Save changes"));
        assertTrue(page.contains("name=\"id\" value=\"4\""));
    }

    @Test
    void typedHtmlIsShownAsTextNotExecuted() {
        Announcement hostile = sample();
        hostile.setTitle("<img src=x onerror=alert(1)>");

        String page = renderPage(pageModel(List.of(hostile), new Announcement()));

        assertFalse(page.contains("<img src=x onerror=alert(1)>"));
        assertTrue(page.contains("&lt;img src=x onerror=alert(1)&gt;"));
    }

    @Test
    void anAnnouncementCanBeAimedAtOneCourse() throws Exception {
        mvc.perform(post("/superadmin/announcements")
                        .param("title", "Course notice").param("message", "Only for SWE")
                        .param("courseId", "10").param("active", "true"))
                .andExpect(status().is3xxRedirection());

        verify(announcementService).save(isNull(), eq("Course notice"), eq("Only for SWE"), isNull(), isNull(),
                eq(10L), eq(true), isNull(), isNull(), eq("root"));
    }

    @Test
    void theFormOffersAllCoursesAndEachCourseAndTheListSaysWhichCourseEachIsFor() {
        Announcement forCourse = sample();
        forCourse.setCourseId(10L);
        Announcement forAll = sample();

        String page = renderPage(pageModel(List.of(forCourse, forAll), new Announcement()));

        assertTrue(page.contains("name=\"courseId\""));
        assertTrue(page.contains(">All courses</option>"));
        assertTrue(page.contains("SWE4949 - Capstone"));
        assertTrue(page.contains("<th scope=\"col\">Course</th>"));
    }
}
