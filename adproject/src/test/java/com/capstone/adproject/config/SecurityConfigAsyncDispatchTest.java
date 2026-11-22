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
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.test.context.ContextConfiguration;

import com.capstone.adproject.service.CustomUserDetailsService;

import jakarta.servlet.DispatcherType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The live progress stream (SSE) finishes through an ASYNC re-dispatch after its response is already committed. If the
 * security filter authorises that dispatch again and the session has expired meanwhile, it throws AuthorizationDeniedException
 * with no way to send a 403 ("response is already committed") and an alert fires. The first (REQUEST) dispatch is what
 * decides access, so the ASYNC one must pass, while a fresh request without a session must still be refused.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = SecurityConfigAsyncDispatchTest.Wiring.class)
class SecurityConfigAsyncDispatchTest {

    @Configuration
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class Wiring {
        @Bean CustomUserDetailsService customUserDetailsService() { return Mockito.mock(CustomUserDetailsService.class); }
        @Bean AuthenticationSuccessHandler successHandler() { return Mockito.mock(AuthenticationSuccessHandler.class); }
        @Bean StreamStub streamStub() { return new StreamStub(); }
    }

    @RestController
    static class StreamStub {
        @GetMapping("/api/progress/stream/{id}")
        String stream() { return "ok"; }
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void theAsyncRedispatchOfAnAlreadyAuthorisedStreamIsNotAuthorisedAgain() throws Exception {
        int status = mvc.perform(get("/api/progress/stream/1").with(request -> {
            request.setDispatcherType(DispatcherType.ASYNC);
            return request;
        })).andReturn().getResponse().getStatus();

        assertEquals(200, status);
    }

    @Test
    void aFreshRequestWithoutASessionIsStillRefused() throws Exception {
        int status = mvc.perform(get("/api/progress/stream/1")).andReturn().getResponse().getStatus();

        assertEquals(401, status);
    }

    @Test
    void aStudentIsStillForbiddenFromTheStream() throws Exception {
        var response = mvc.perform(get("/api/progress/stream/1").with(user("s@x.com").roles("STUDENT")))
                .andReturn().getResponse();

        // Access denied for a signed-in user is forwarded to the error page (the mock keeps status 200 for a forward).
        assertEquals("/error", response.getForwardedUrl());
    }
}
