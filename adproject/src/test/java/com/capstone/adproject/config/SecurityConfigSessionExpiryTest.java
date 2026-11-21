package com.capstone.adproject.config;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

import com.capstone.adproject.exception.GlobalExceptionHandler;
import com.capstone.adproject.service.CustomUserDetailsService;

import jakarta.servlet.RequestDispatcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** An expired session must send people to the login page, not to an error page. */
class SecurityConfigSessionExpiryTest {

    private SecurityConfig config;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        config = new SecurityConfig(mock(CustomUserDetailsService.class));
        request = new MockHttpServletRequest("GET", "/student/home");
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void pageNavigationWithAnExpiredSessionRedirectsToLoginWithAnExpiredNotice() throws Exception {
        request.addHeader("Accept", "text/html,application/xhtml+xml");
        request.setRequestedSessionId("EXPIRED-SESSION");
        request.setRequestedSessionIdValid(false);

        config.authenticationEntryPoint().commence(request, response, new InsufficientAuthenticationException("x"));

        assertEquals("/login?expired", response.getRedirectedUrl());
    }

    @Test
    void pageNavigationWithNoSessionRedirectsToPlainLogin() throws Exception {
        request.addHeader("Accept", "text/html");

        config.authenticationEntryPoint().commence(request, response, new InsufficientAuthenticationException("x"));

        assertEquals("/login", response.getRedirectedUrl());
    }

    @Test
    void scriptCallsGetAJson401SoThePageCanReact() throws Exception {
        request.addHeader("X-Requested-With", "XMLHttpRequest");

        config.authenticationEntryPoint().commence(request, response, new InsufficientAuthenticationException("x"));

        assertEquals(401, response.getStatus());
        assertNull(response.getRedirectedUrl());
        assertTrue(response.getContentAsString().contains("session_expired"));
    }

    @Test
    void liveProgressStreamGetsABare401WithNoBody() throws Exception {
        request.setRequestURI("/api/progress/stream/6");
        request.addHeader("Accept", "text/event-stream");

        config.authenticationEntryPoint().commence(request, response, new InsufficientAuthenticationException("x"));

        assertEquals(401, response.getStatus());
        assertEquals("", response.getContentAsString());
    }

    @Test
    void formPostAfterTheSessionExpiredIsTreatedAsExpiryNotAsForbidden() throws Exception {
        request = new MockHttpServletRequest("POST", "/admin/save-student");
        request.addHeader("Accept", "text/html");
        request.setRequestedSessionId("EXPIRED-SESSION");
        request.setRequestedSessionIdValid(false);

        config.accessDeniedHandler().handle(request, response, new MissingCsrfTokenException("none"));

        assertEquals("/login?expired", response.getRedirectedUrl());
    }

    @Test
    void csrfFailureForASignedInUserExplainsTheExpiredFormInsteadOfRedirecting() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin@example.com", "x", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        request = new MockHttpServletRequest("POST", "/admin/save-student");
        request.addHeader("Accept", "text/html");

        config.accessDeniedHandler().handle(request, response, new MissingCsrfTokenException("none"));

        assertEquals("/error", response.getForwardedUrl());
        assertEquals(403, request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE));
        assertNotNull(request.getAttribute(GlobalExceptionHandler.CUSTOM_MESSAGE_ATTRIBUTE));
    }
}
