package com.capstone.adproject.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import com.capstone.adproject.config.GoogleLoginStatus;
import com.capstone.adproject.service.EmailService;
import com.capstone.adproject.service.UserService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** The login page: a Google button only when Google is configured, and a clear message when Google sign-in fails. */
class LoginPageGoogleTest {

    private AuthController controller(boolean googleOn) {
        AuthController controller = new AuthController(mock(UserService.class), mock(EmailService.class));
        controller.setGoogleLoginStatus(new GoogleLoginStatus(googleOn));
        return controller;
    }

    private Map<String, Object> modelFor(boolean googleOn, String googleParam) throws Exception {
        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver("/templates/", ".html");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller(googleOn)).setViewResolvers(viewResolver).build();
        var request = googleParam == null ? get("/login") : get("/login").param("google", googleParam);
        return mvc.perform(request).andReturn().getModelAndView().getModel();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theControllerTellsThePageWhetherGoogleIsAvailable() throws Exception {
        assertEquals(true, modelFor(true, null).get("googleLoginEnabled"));
        assertEquals(false, modelFor(false, null).get("googleLoginEnabled"));
    }

    @Test
    void withoutAGoogleStatusBeanTheButtonIsOff() throws Exception {
        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver("/templates/", ".html");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AuthController(mock(UserService.class), mock(EmailService.class)))
                .setViewResolvers(viewResolver).build();

        assertEquals(false, mvc.perform(get("/login")).andReturn().getModelAndView().getModel().get("googleLoginEnabled"));
    }

    @Test
    void eachGoogleFailureHasItsOwnPlainMessage() throws Exception {
        String notRegistered = (String) modelFor(true, "not_registered").get("googleMessage");
        String unverified = (String) modelFor(true, "unverified").get("googleMessage");
        String error = (String) modelFor(true, "error").get("googleMessage");

        assertTrue(notRegistered.contains("not registered"), notRegistered);
        assertTrue(notRegistered.contains("password"), "it says what to do instead: " + notRegistered);
        assertTrue(unverified.contains("verify"), unverified);
        assertTrue(error.contains("try again"), error);
    }

    @Test
    void anUnknownGoogleValueGetsTheGenericMessageAndNeverEchoesTheInput() throws Exception {
        String message = (String) modelFor(true, "<script>alert(1)</script>").get("googleMessage");

        assertTrue(message.contains("try again"));
        assertFalse(message.contains("script"));
    }

    @Test
    void noGoogleParameterMeansNoMessage() throws Exception {
        assertNull(modelFor(true, null).get("googleMessage"));
    }

    private String renderLogin(Map<String, Object> model) {
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
        return engine.process("login", context);
    }

    @Test
    void thePageShowsAGoogleButtonThatStartsTheGoogleFlow() {
        String html = renderLogin(Map.of("googleLoginEnabled", true));

        assertTrue(html.contains("/oauth2/authorization/google"), "the button links to Spring's Google entry point");
        assertTrue(html.contains("Sign in with Google"));
        assertTrue(html.contains("name=\"password\""), "password login is still there");
    }

    @Test
    void withoutGoogleThePageHasNoGoogleButton() {
        String html = renderLogin(Map.of("googleLoginEnabled", false));

        assertFalse(html.contains("oauth2/authorization"));
        assertFalse(html.contains("Sign in with Google"));
    }

    @Test
    void aGoogleMessageIsShownEscaped() {
        String html = renderLogin(Map.of("googleLoginEnabled", true, "googleMessage", "That Google account is <b>not</b> registered."));

        assertTrue(html.contains("That Google account is &lt;b&gt;not&lt;/b&gt; registered."));
    }
}
