package com.capstone.adproject.config;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * The signed-in person after "Sign in with Google": Google's identity, carrying the roles of the account IAMS has for that
 * email. It is also a {@link UserDetails}, so the rest of the app sees the same kind of principal as after a password login,
 * and its name is the stored email, which is how the app identifies people everywhere else.
 */
public class RegisteredGoogleUser extends DefaultOidcUser implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final String username;

    public RegisteredGoogleUser(UserDetails registered, OidcUser google) {
        super(registered.getAuthorities(), google.getIdToken(), google.getUserInfo(), "sub");
        this.username = registered.getUsername();
    }

    @Override
    public String getName() {
        return username;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return null; // Google proved the identity; there is no password to hold
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /** Only the stored email: the default would print Google's whole profile and the ID token into any log line. */
    @Override
    public String toString() {
        return "RegisteredGoogleUser[" + username + "]";
    }
}
