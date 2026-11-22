package com.capstone.adproject.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Whatever goes wrong with Google, the person lands back on the login page with a reason they can act on. */
class GoogleSignInFailureHandlerTest {

    private final GoogleSignInFailureHandler handler = new GoogleSignInFailureHandler();

    private String redirectFor(Exception failure) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, (org.springframework.security.core.AuthenticationException) failure);
        return response.getRedirectedUrl();
    }

    @Test
    void anUnregisteredEmailSaysSo() throws Exception {
        assertEquals("/login?google=not_registered", redirectFor(
                new OAuth2AuthenticationException(new OAuth2Error(RegisteredGoogleUserService.NOT_REGISTERED))));
    }

    @Test
    void anUnverifiedEmailSaysSo() throws Exception {
        assertEquals("/login?google=unverified", redirectFor(
                new OAuth2AuthenticationException(new OAuth2Error(RegisteredGoogleUserService.EMAIL_UNVERIFIED))));
    }

    @Test
    void anythingElseIsAGenericFailureThatRevealsNothing() throws Exception {
        assertEquals("/login?google=error", redirectFor(new OAuth2AuthenticationException(new OAuth2Error("invalid_grant", "secret detail", null))));
        assertEquals("/login?google=error", redirectFor(new BadCredentialsException("secret detail")));
    }
}
