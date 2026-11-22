package com.capstone.adproject.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.capstone.adproject.service.CustomUserDetailsService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** The real security chain with Google switched on: where the button leads, and what the callback does when it goes wrong. */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = GoogleSignInWiringTest.Wiring.class)
@TestPropertySource(properties = {
        "app.google.client-id=test-client-id.apps.googleusercontent.com",
        "app.google.client-secret=test-secret"})
class GoogleSignInWiringTest {

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, GoogleLoginConfig.class})
    static class Wiring {
        @Bean CustomUserDetailsService customUserDetailsService() { return Mockito.mock(CustomUserDetailsService.class); }
        @Bean AuthenticationSuccessHandler successHandler() { return Mockito.mock(AuthenticationSuccessHandler.class); }
        @Bean Page page() { return new Page(); }
    }

    @RestController
    static class Page {
        @GetMapping("/admin/home")
        String home() { return "home"; }
    }

    @Autowired private WebApplicationContext context;
    @Autowired private GoogleLoginStatus status;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void theLoginPageIsToldGoogleIsAvailable() {
        assertTrue(status.isEnabled());
    }

    @Test
    void theGoogleButtonSendsThePersonToGoogleWithOpenIdAndEmailScopes() throws Exception {
        var response = mvc.perform(get("/oauth2/authorization/google")).andReturn().getResponse();

        assertEquals(302, response.getStatus());
        String location = response.getRedirectedUrl();
        assertNotNull(location);
        assertTrue(location.startsWith("https://accounts.google.com/o/oauth2/v2/auth"), location);
        assertTrue(location.contains("client_id=test-client-id.apps.googleusercontent.com"), location);
        assertTrue(location.contains("scope=openid%20email") && !location.contains("profile"), "only the email is asked for: " + location);
        assertTrue(location.contains("state="), "a state value protects against forged callbacks");
        assertTrue(location.contains("redirect_uri="), location);
        assertFalse(location.contains("test-secret"), "the client secret never goes through the browser");
    }

    @Test
    void aCallbackThatGoogleDidNotStartIsRefusedAndSentBackToTheLoginPage() throws Exception {
        var response = mvc.perform(get("/login/oauth2/code/google").param("code", "forged").param("state", "forged"))
                .andReturn().getResponse();

        assertEquals("/login?google=error", response.getRedirectedUrl());
    }

    @Test
    void theReturnAddressIsTheConfiguredOneThenThePublicBaseUrlThenTheRequestsOwn() {
        assertEquals("https://x.example/cb", GoogleLoginConfig.redirectUriFor(" https://x.example/cb ", "https://iams.example"));
        assertEquals("https://iams.example/login/oauth2/code/google", GoogleLoginConfig.redirectUriFor("", "https://iams.example/"));
        assertEquals("{baseUrl}/login/oauth2/code/{registrationId}", GoogleLoginConfig.redirectUriFor("", ""));
    }

    @Test
    void protectedPagesStillNeedASignInAndScriptsStillGetA401() throws Exception {
        var response = mvc.perform(get("/admin/home")).andReturn().getResponse();
        assertEquals("/login", response.getRedirectedUrl());
        assertEquals(401, mvc.perform(get("/admin/home").header("X-Requested-With", "XMLHttpRequest")).andReturn().getResponse().getStatus());
    }
}
