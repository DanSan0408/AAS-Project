package com.capstone.adproject.config;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "Sign in with Google" only proves who the person is. It never creates an account: the verified Google email has to be one
 * IAMS already knows, and the person then gets exactly the roles that email has with a password login.
 */
class RegisteredGoogleUserServiceTest {

    private final UserDetailsService registered = mock(UserDetailsService.class);

    private static OidcUser googleUser(String email, Object verified) {
        Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("sub", "1234567890");
        claims.put("name", "Someone Real");
        claims.put("picture", "https://example.com/p.png");
        if (email != null) {
            claims.put("email", email);
        }
        if (verified != null) {
            claims.put("email_verified", verified);
        }
        OidcIdToken token = new OidcIdToken("token-value", Instant.now(), Instant.now().plusSeconds(60), claims);
        return new DefaultOidcUser(List.of(), token, "sub");
    }

    private RegisteredGoogleUserService serviceFor(OidcUser fromGoogle) {
        return new RegisteredGoogleUserService(request -> fromGoogle, registered);
    }

    private static UserDetails stored(String email, String... roles) {
        List<GrantedAuthority> authorities = new java.util.ArrayList<>();
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority(role));
        }
        return new User(email, "{bcrypt}hash", authorities);
    }

    @Test
    void aRegisteredEmailSignsInWithTheSameRolesAsAPasswordLogin() {
        when(registered.loadUserByUsername("lecturer@uni.edu")).thenReturn(stored("lecturer@uni.edu", "ROLE_LECTURER", "ROLE_SUPERVISOR"));

        OidcUser principal = serviceFor(googleUser("lecturer@uni.edu", true)).loadUser(null);

        assertEquals(List.of("ROLE_LECTURER", "ROLE_SUPERVISOR"),
                principal.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }

    @Test
    void theSignedInNameIsTheStoredEmailNotGooglesSpellingOfIt() {
        when(registered.loadUserByUsername("Lecturer@Uni.edu")).thenReturn(stored("lecturer@uni.edu", "ROLE_LECTURER"));

        OidcUser principal = serviceFor(googleUser("Lecturer@Uni.edu", true)).loadUser(null);

        assertEquals("lecturer@uni.edu", principal.getName(), "the rest of the app identifies people by their stored email");
        assertTrue(principal instanceof UserDetails);
        assertEquals("lecturer@uni.edu", ((UserDetails) principal).getUsername());
    }

    @Test
    void anEmailIamsDoesNotKnowIsRefusedAndNothingIsCreated() {
        when(registered.loadUserByUsername(anyString())).thenThrow(new UsernameNotFoundException("nobody"));

        OAuth2AuthenticationException refused = assertThrows(OAuth2AuthenticationException.class,
                () -> serviceFor(googleUser("stranger@gmail.com", true)).loadUser(null));

        assertEquals(RegisteredGoogleUserService.NOT_REGISTERED, refused.getError().getErrorCode());
    }

    @Test
    void anUnverifiedGoogleEmailIsRefusedWithoutEvenLookingItUp() {
        for (Object notVerified : new Object[]{false, "false", null}) {
            OAuth2AuthenticationException refused = assertThrows(OAuth2AuthenticationException.class,
                    () -> serviceFor(googleUser("lecturer@uni.edu", notVerified)).loadUser(null));
            assertEquals(RegisteredGoogleUserService.EMAIL_UNVERIFIED, refused.getError().getErrorCode(), String.valueOf(notVerified));
        }
        verify(registered, never()).loadUserByUsername(anyString());
    }

    @Test
    void aMissingOrBlankEmailIsRefused() {
        for (String email : new String[]{null, "", "   "}) {
            OAuth2AuthenticationException refused = assertThrows(OAuth2AuthenticationException.class,
                    () -> serviceFor(googleUser(email, true)).loadUser(null));
            assertEquals(RegisteredGoogleUserService.EMAIL_UNVERIFIED, refused.getError().getErrorCode());
        }
        verify(registered, never()).loadUserByUsername(anyString());
    }

    @Test
    void theGoogleProfileIsNotLeakedIntoLogsThroughToString() {
        when(registered.loadUserByUsername("lecturer@uni.edu")).thenReturn(stored("lecturer@uni.edu", "ROLE_LECTURER"));

        String text = serviceFor(googleUser("lecturer@uni.edu", true)).loadUser(null).toString();

        assertTrue(text.contains("lecturer@uni.edu"));
        assertFalse(text.contains("Someone Real"));
        assertFalse(text.contains("1234567890"));
        assertFalse(text.contains("token-value"));
    }
}
