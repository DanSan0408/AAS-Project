package com.capstone.adproject.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

import com.capstone.adproject.service.CustomUserDetailsService;

/**
 * "Sign in with Google". Everything here is off unless GOOGLE_CLIENT_ID (app.google.client-id) is set, so the app starts and
 * the login page works exactly as before without it. Only the "openid" and "email" scopes are requested: the email is all
 * IAMS needs, and nothing else about the person is read.
 */
@Configuration
public class GoogleLoginConfig {

    private static final String GOOGLE_ENABLED = "!'${app.google.client-id:}'.isBlank()";

    @Bean
    public GoogleLoginStatus googleLoginStatus(@Value("${app.google.client-id:}") String clientId) {
        return new GoogleLoginStatus(!clientId.isBlank());
    }

    @Bean
    @ConditionalOnExpression(GOOGLE_ENABLED)
    public ClientRegistrationRepository googleClientRegistrations(
            @Value("${app.google.client-id}") String clientId,
            @Value("${app.google.client-secret:}") String clientSecret,
            @Value("${app.google.redirect-uri:}") String redirectUri,
            @Value("${app.public-base-url:}") String publicBaseUrl) {
        ClientRegistration.Builder google = ClientRegistration.withRegistrationId("google")
                .clientName("Google")
                .clientId(clientId.trim())
                .clientSecret(clientSecret.trim())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUriFor(redirectUri, publicBaseUrl))
                .scope(OidcScopes.OPENID, OidcScopes.EMAIL)
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://www.googleapis.com/oauth2/v4/token")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .issuerUri("https://accounts.google.com")
                .userNameAttributeName(IdTokenClaimNames.SUB);
        return new InMemoryClientRegistrationRepository(google.build());
    }

    /**
     * The address Google sends the person back to. Explicit setting first, then the public base URL (so behind the proxy it is
     * the https address registered in Google Cloud, not the internal address the request arrived on), then the request's own.
     */
    static String redirectUriFor(String explicit, String publicBaseUrl) {
        if (!explicit.isBlank()) {
            return explicit.trim();
        }
        if (!publicBaseUrl.isBlank()) {
            return publicBaseUrl.trim().replaceAll("/+$", "") + "/login/oauth2/code/google";
        }
        return "{baseUrl}/login/oauth2/code/{registrationId}";
    }

    @Bean
    @ConditionalOnExpression(GOOGLE_ENABLED)
    public RegisteredGoogleUserService registeredGoogleUserService(CustomUserDetailsService registeredUsers) {
        return new RegisteredGoogleUserService(registeredUsers);
    }

    @Bean
    @ConditionalOnExpression(GOOGLE_ENABLED)
    public GoogleSignInFailureHandler googleSignInFailureHandler() {
        return new GoogleSignInFailureHandler();
    }
}
