package com.capstone.adproject.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Sends a failed Google sign-in back to the login page with a reason the person can act on and nothing else. */
public class GoogleSignInFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(GoogleSignInFailureHandler.class);

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        String reason = "error";
        if (exception instanceof OAuth2AuthenticationException oauth) {
            String code = oauth.getError().getErrorCode();
            if (RegisteredGoogleUserService.NOT_REGISTERED.equals(code)) {
                reason = "not_registered";
            } else if (RegisteredGoogleUserService.EMAIL_UNVERIFIED.equals(code)) {
                reason = "unverified";
            } else {
                log.warn("Google sign-in failed ({})", code);
            }
        } else {
            log.warn("Google sign-in failed ({})", exception.getClass().getSimpleName());
        }
        response.sendRedirect(request.getContextPath() + "/login?google=" + reason);
    }
}
