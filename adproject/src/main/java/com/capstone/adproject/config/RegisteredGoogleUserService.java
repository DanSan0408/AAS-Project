package com.capstone.adproject.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

/**
 * Turns a Google identity into an IAMS sign-in. Google only proves who the person is: the verified email must belong to an
 * account IAMS already has (the same lookup a password login uses), and nothing is ever created here.
 */
public class RegisteredGoogleUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    public static final String NOT_REGISTERED = "google_not_registered";
    public static final String EMAIL_UNVERIFIED = "google_email_unverified";

    private static final Logger log = LoggerFactory.getLogger(RegisteredGoogleUserService.class);

    private final OAuth2UserService<OidcUserRequest, OidcUser> googleIdentity;
    private final UserDetailsService registeredUsers;

    /** Production: Google's signature-checked ID token is the identity; no extra call to Google is needed for the email. */
    public RegisteredGoogleUserService(UserDetailsService registeredUsers) {
        this(request -> {
            OidcIdToken idToken = request.getIdToken();
            return new DefaultOidcUser(List.of(new OidcUserAuthority(idToken)), idToken, "sub");
        }, registeredUsers);
    }

    RegisteredGoogleUserService(OAuth2UserService<OidcUserRequest, OidcUser> googleIdentity, UserDetailsService registeredUsers) {
        this.googleIdentity = googleIdentity;
        this.registeredUsers = registeredUsers;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser google = googleIdentity.loadUser(request);
        String email = google.getEmail() == null ? "" : google.getEmail().trim();
        if (email.isEmpty() || !isTrue(google.getClaim("email_verified"))) {
            log.info("Google sign-in refused: the email is missing or not verified by Google");
            throw refused(EMAIL_UNVERIFIED);
        }
        UserDetails registered;
        try {
            registered = registeredUsers.loadUserByUsername(email);
        } catch (UsernameNotFoundException unknown) {
            log.info("Google sign-in refused: the email is not registered in IAMS");
            throw refused(NOT_REGISTERED);
        }
        return new RegisteredGoogleUser(registered, google);
    }

    private static boolean isTrue(Object claim) {
        return Boolean.TRUE.equals(claim);
    }

    private static OAuth2AuthenticationException refused(String code) {
        return new OAuth2AuthenticationException(new OAuth2Error(code));
    }

}
