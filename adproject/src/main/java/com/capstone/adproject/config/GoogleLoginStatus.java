package com.capstone.adproject.config;

/** Whether "Sign in with Google" is configured, so the login page only shows a button that works. */
public class GoogleLoginStatus {

    private final boolean enabled;

    public GoogleLoginStatus(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
