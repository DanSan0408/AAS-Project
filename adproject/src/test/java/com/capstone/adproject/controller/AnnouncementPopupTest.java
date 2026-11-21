package com.capstone.adproject.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
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
import org.thymeleaf.templateresolver.StringTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import com.capstone.adproject.service.AnnouncementService;
import com.capstone.adproject.service.CourseScopeService;
import com.capstone.adproject.service.AnnouncementService.AnnouncementView;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The login pop-up: who gets it, that it appears once per login, and that it renders safely. */
class AnnouncementPopupTest {

    private final AnnouncementService service = mock(AnnouncementService.class);
    private final CourseScopeService courseScope = mock(CourseScopeService.class);
    private final AnnouncementAdvice advice = new AnnouncementAdvice(service, courseScope);

    private static final AnnouncementView NOTICE = new AnnouncementView(1L, "Nightly maintenance",
            "The system is offline 02:00-02:30.", "maintenance", "Maintenance", "19 Nov 2026");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest requestWithSession(MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        return request;
    }

    private static void signInAs(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("someone", "n/a", AuthorityUtils.createAuthorityList(roles)));
    }

    @Test
    void firstPageAfterLoginGetsTheLiveAnnouncements() {
        signInAs("ROLE_STUDENT");
        when(service.liveFor(anyCollection(), any(), any())).thenReturn(List.of(NOTICE));

        List<AnnouncementView> pending = advice.pendingAnnouncements(requestWithSession(new MockHttpSession()));

        assertEquals(List.of(NOTICE), pending);
    }

    @Test
    void theDatabaseIsAskedOncePerLoginNotOncePerPage() {
        signInAs("ROLE_LECTURER");
        when(service.liveFor(anyCollection(), any(), any())).thenReturn(List.of(NOTICE));
        MockHttpSession session = new MockHttpSession();

        advice.pendingAnnouncements(requestWithSession(session));
        advice.pendingAnnouncements(requestWithSession(session));
        List<AnnouncementView> third = advice.pendingAnnouncements(requestWithSession(session));

        assertEquals(List.of(NOTICE), third);
        verify(service, times(1)).liveFor(anyCollection(), any(), any());
    }

    @Test
    void afterDismissalNothingIsShownAgainInThatSession() {
        signInAs("ROLE_STUDENT");
        when(service.liveFor(anyCollection(), any(), any())).thenReturn(List.of(NOTICE));
        MockHttpSession session = new MockHttpSession();
        advice.pendingAnnouncements(requestWithSession(session));

        AnnouncementAdvice.markSeen(session);

        assertTrue(advice.pendingAnnouncements(requestWithSession(session)).isEmpty());
        verify(service, times(1)).liveFor(anyCollection(), any(), any());
    }

    @Test
    void aNewLoginIsANewSessionAndSeesTheAnnouncementsAgain() {
        signInAs("ROLE_STUDENT");
        when(service.liveFor(anyCollection(), any(), any())).thenReturn(List.of(NOTICE));
        MockHttpSession firstLogin = new MockHttpSession();
        advice.pendingAnnouncements(requestWithSession(firstLogin));
        AnnouncementAdvice.markSeen(firstLogin);

        List<AnnouncementView> secondLogin = advice.pendingAnnouncements(requestWithSession(new MockHttpSession()));

        assertEquals(List.of(NOTICE), secondLogin);
    }

    @Test
    void nobodyIsAskedBeforeSigningIn() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertTrue(advice.pendingAnnouncements(requestWithSession(new MockHttpSession())).isEmpty());
        verify(service, never()).liveFor(anyCollection(), any(), any());
    }

    @Test
    void aBrokenAnnouncementLookupNeverBreaksThePage() {
        signInAs("ROLE_STUDENT");
        when(service.liveFor(anyCollection(), any(), any())).thenThrow(new IllegalStateException("table missing"));
        MockHttpSession session = new MockHttpSession();

        assertTrue(advice.pendingAnnouncements(requestWithSession(session)).isEmpty());
        assertTrue(advice.pendingAnnouncements(requestWithSession(session)).isEmpty());
        verify(service, times(1)).liveFor(anyCollection(), any(), any());
    }

    @Test
    void dismissEndpointClearsThePendingListAndAnswersNoContent() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AnnouncementController()).build();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AnnouncementAdvice.SESSION_KEY, new ArrayList<>(List.of(NOTICE)));

        mvc.perform(post("/announcements/dismiss").session(session)).andExpect(status().isNoContent());

        assertTrue(((List<?>) session.getAttribute(AnnouncementAdvice.SESSION_KEY)).isEmpty());
    }

    @Test
    void theAnnouncementsAreAskedForInTheCourseThePersonIsWorkingIn() {
        signInAs("ROLE_STUDENT");
        when(courseScope.getActiveCourseIdForCurrentUser()).thenReturn(10L);
        when(service.liveFor(anyCollection(), any(), org.mockito.ArgumentMatchers.eq(10L))).thenReturn(List.of(NOTICE));

        assertEquals(List.of(NOTICE), advice.pendingAnnouncements(requestWithSession(new MockHttpSession())));
    }

    @Test
    void switchingCourseShowsThatCoursesAnnouncementsButNeverOnesAlreadyDismissed() {
        signInAs("ROLE_STUDENT");
        AnnouncementView forEveryone = new AnnouncementView(1L, "Everyone", "All courses.", "notice", "Notice", "19 Nov 2026");
        AnnouncementView forCourse20 = new AnnouncementView(2L, "Course 20", "Only here.", "notice", "Notice", "19 Nov 2026");
        when(service.liveFor(anyCollection(), any(), org.mockito.ArgumentMatchers.eq(10L))).thenReturn(List.of(forEveryone));
        when(service.liveFor(anyCollection(), any(), org.mockito.ArgumentMatchers.eq(20L))).thenReturn(List.of(forEveryone, forCourse20));
        MockHttpSession session = new MockHttpSession();

        when(courseScope.getActiveCourseIdForCurrentUser()).thenReturn(10L);
        assertEquals(List.of(forEveryone), advice.pendingAnnouncements(requestWithSession(session)));
        AnnouncementAdvice.markSeen(session);
        assertTrue(advice.pendingAnnouncements(requestWithSession(session)).isEmpty());

        when(courseScope.getActiveCourseIdForCurrentUser()).thenReturn(20L);
        assertEquals(List.of(forCourse20), advice.pendingAnnouncements(requestWithSession(session)));
    }

    @Test
    void withNoActiveCourseOnlyTheEveryCourseAnnouncementsAreAsked() {
        signInAs("ROLE_LECTURER");
        when(courseScope.getActiveCourseIdForCurrentUser()).thenReturn(null);
        when(service.liveFor(anyCollection(), any(), org.mockito.ArgumentMatchers.isNull())).thenReturn(List.of(NOTICE));

        assertEquals(List.of(NOTICE), advice.pendingAnnouncements(requestWithSession(new MockHttpSession())));
    }

    // --- rendering -----------------------------------------------------------------------------------------

    private static String renderPopup(Map<String, Object> model) {
        var inline = new StringTemplateResolver();
        inline.setResolvablePatterns(Set.of("<*"));
        inline.setOrder(1);
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setOrder(2);
        var engine = new SpringTemplateEngine();
        engine.addTemplateResolver(inline);
        engine.addTemplateResolver(resolver);

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
        return engine.process("<div th:replace=\"~{fragments/announcement_popup :: popup}\"></div>", context);
    }

    @Test
    void popupShowsTitleMessageTypeAndADismissButton() {
        String html = renderPopup(Map.of("pendingAnnouncements", List.of(NOTICE)));

        assertTrue(html.contains("id=\"announcementDialog\""));
        assertTrue(html.contains("Nightly maintenance"));
        assertTrue(html.contains("The system is offline 02:00-02:30."));
        assertTrue(html.contains("Maintenance"));
        assertTrue(html.contains("/announcements/dismiss"));
        assertTrue(html.contains("Got it"));
    }

    @Test
    void popupRendersNothingWhenThereIsNothingToSay() {
        assertFalse(renderPopup(Map.of("pendingAnnouncements", List.of())).contains("announcementDialog"));
        assertFalse(renderPopup(Map.of()).contains("announcementDialog"));
    }

    @Test
    void popupEscapesHtmlTheSuperAdminTypes() {
        AnnouncementView hostile = new AnnouncementView(2L, "<b>Bold</b>", "<script>alert('x')</script>",
                "notice", "Notice", "19 Nov 2026");

        String html = renderPopup(Map.of("pendingAnnouncements", List.of(hostile)));

        assertFalse(html.contains("<script>alert('x')</script>"));
        assertTrue(html.contains("&lt;script&gt;alert("));
        assertFalse(html.contains("<b>Bold</b>"));
    }
}
