package com.capstone.adproject.config;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfException;
import static org.springframework.security.config.Customizer.withDefaults;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices.RememberMeTokenAlgorithm;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.capstone.adproject.exception.GlobalExceptionHandler;
import com.capstone.adproject.service.CustomUserDetailsService;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    private final CustomUserDetailsService userDetailsService;

    @Value("${app.security.remember-me-key:}")
    private String configuredRememberMeKey;

    private String resolvedRememberMeKey;

    public SecurityConfig(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    private synchronized String getOrGenerateRememberMeKey() {
        if (resolvedRememberMeKey != null) {
            return resolvedRememberMeKey;
        }
        if (configuredRememberMeKey != null && !configuredRememberMeKey.isBlank()) {
            resolvedRememberMeKey = configuredRememberMeKey.trim();
        } else {
            logger.warn("SECURITY WARNING: 'app.security.remember-me-key' is not configured. Generating a random ephemeral 256-bit key. Existing remember-me cookies will be invalidated across restarts.");
            byte[] randomBytes = new byte[32];
            new SecureRandom().nextBytes(randomBytes);
            resolvedRememberMeKey = Base64.getEncoder().encodeToString(randomBytes);
        }
        return resolvedRememberMeKey;
    }

    //encrypt password guna BCrypt
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Autowired
    private AuthenticationSuccessHandler authenticationSuccessHandler;

    // Present only when GOOGLE_CLIENT_ID is configured (see GoogleLoginConfig); otherwise there is no Google sign-in at all.
    @Autowired(required = false)
    private ClientRegistrationRepository googleClientRegistrations;

    @Autowired(required = false)
    private RegisteredGoogleUserService registeredGoogleUsers;

    @Autowired(required = false)
    private GoogleSignInFailureHandler googleSignInFailureHandler;

    //to save and restore user last visited page if dia keluar dari system and still ada cookie
    @Bean
    public RequestCache requestCache() {
        return new HttpSessionRequestCache();
    }

    //cookies function
    @Bean
    public RememberMeServices rememberMeServices() {
        TokenBasedRememberMeServices rememberMeServices = 
            new TokenBasedRememberMeServices(
                getOrGenerateRememberMeKey(), 
                userDetailsService, 
                RememberMeTokenAlgorithm.SHA256
            );
        
        rememberMeServices.setParameter("remember-me");
        rememberMeServices.setCookieName("utm-cas-remember-me");
        rememberMeServices.setTokenValiditySeconds(604800); // 7 days
        rememberMeServices.setAlwaysRemember(false);
        
        return rememberMeServices;
    }

    //role based filter on the users
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
        // Apply CORS configuration first
            .cors(withDefaults())
            // Disable CSRF for the CSP report endpoint, as it's a browser-sent POST without a token
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/csp-violations"))
        //authenticate and authorize
            .authenticationProvider(daoAuthenticationProvider())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint())
                .accessDeniedHandler(accessDeniedHandler()))
            .authorizeHttpRequests(authorize -> authorize
                // ASYNC: the live progress stream (SSE) completes through an async re-dispatch after its response is committed. The
                // first (REQUEST) dispatch already decided access; authorising the re-dispatch again throws
                // AuthorizationDeniedException with no way to answer once the session has expired.
                .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD, DispatcherType.ASYNC).permitAll()
                .requestMatchers(HttpMethod.OPTIONS).permitAll()
                .requestMatchers("/", "/login", "/favicon.ico", "/actuator/health", "/css/**", "/js/**", "/images/**",
                    "/forgot_password", "/reset_password/**", "/csp-violations", "/error", "/error/**",
                    "/oauth2/authorization/**", "/login/oauth2/code/**").permitAll()
                .requestMatchers("/superadmin/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN") // Super Admin can access admin pages
                .requestMatchers("/rubrics/**").hasAnyRole("ADMIN", "SUPER_ADMIN") // Super Admin can access rubric pages
                .requestMatchers("/manage-assessments", "/assessments/manage").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers("/deadlines/**").hasAnyRole("ADMIN", "SUPER_ADMIN") // Explicitly lock orphaned controllers
                .requestMatchers("/api/**").hasAnyRole("ADMIN", "SUPER_ADMIN", "LECTURER") // Protect future REST calls
                .requestMatchers("/*/comments/view/**").hasAnyRole("ADMIN", "SUPER_ADMIN", "LECTURER") // Prevent {role} dynamic path bypass
                .requestMatchers("/student/**", "/student/assessment/**").hasRole("STUDENT")
                .requestMatchers("/lecturer/**").hasRole("LECTURER")
                .anyRequest().authenticated()
            ) //login
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(authenticationSuccessHandler)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            
            //pastikan guna/tarik remember me punya service
            .rememberMe(rememberMe -> rememberMe
                .rememberMeServices(rememberMeServices())
            )
            
            //cache ke last visited page
            .requestCache(cache -> cache
                .requestCache(requestCache())
            )
            
            //logout and clear cookies, cache
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .deleteCookies("JSESSIONID", "utm-cas-remember-me")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .permitAll()
            )
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> {
                    String policy = "default-src 'self' https:; " +
                                    "script-src 'self' 'unsafe-inline' 'unsafe-eval' https:; " +
                                    "style-src 'self' 'unsafe-inline' https:; " + 
                                    "img-src 'self' data: https:; " +
                                    "font-src 'self' data: https:; " +
                                    "connect-src 'self' https:; " +
                                    "frame-src 'none'; object-src 'none'; " +
                                    "report-uri /csp-violations;";
                    csp.policyDirectives(policy);
                })
            );

        if (googleClientRegistrations != null && registeredGoogleUsers != null && googleSignInFailureHandler != null) {
            http.oauth2Login(oauth -> oauth
                .loginPage("/login")
                .clientRegistrationRepository(googleClientRegistrations)
                .userInfoEndpoint(userInfo -> userInfo.oidcUserService(registeredGoogleUsers))
                .successHandler(authenticationSuccessHandler)
                .failureHandler(googleSignInFailureHandler)
            );
        }

        return http.build();
    }

    //check dengan database the user
    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setPasswordEncoder(passwordEncoder());
        provider.setUserDetailsService(userDetailsService);
        return provider;
    }

    /**
     * Unauthenticated access to a protected URL. A browser page navigation is sent to the login page (and the
     * original URL is remembered by the request cache so the user lands back on it after signing in); a script or
     * API call gets a bare 401 it can react to. Showing an error page here is what made expired sessions look like
     * a system fault.
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> rejectUnauthenticated(request, response);
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean signedIn = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
            // A CSRF failure with no live session is an expired session, not a forbidden action.
            if (accessDeniedException instanceof CsrfException && (!signedIn || !request.isRequestedSessionIdValid())) {
                rejectUnauthenticated(request, response);
                return;
            }
            if (accessDeniedException instanceof CsrfException) {
                request.setAttribute(GlobalExceptionHandler.CUSTOM_MESSAGE_ATTRIBUTE,
                        "This page was open for too long and its security token expired. Reload the page and try again.");
            }
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.FORBIDDEN.value());
            request.getRequestDispatcher("/error").forward(request, response);
        };
    }

    private static void rejectUnauthenticated(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (GlobalExceptionHandler.wantsJson(request) || isEventStream(request)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            if (!isEventStream(request)) {
                response.setContentType("application/json");
                response.getWriter().write("{\"status\":401,\"error\":\"session_expired\","
                        + "\"message\":\"Your session has expired. Please sign in again.\"}");
            }
            return;
        }
        // A session cookie that no longer maps to a live session means it expired; no cookie means never signed in.
        boolean expired = request.getRequestedSessionId() != null && !request.isRequestedSessionIdValid();
        response.sendRedirect(request.getContextPath() + (expired ? "/login?expired" : "/login"));
    }

    private static boolean isEventStream(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/event-stream");
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // IMPORTANT: Explicitly whitelist the domains of your frontend applications.
        // This tells the server it's safe to accept requests from these origins.
        // Avoid using "*" when `allowCredentials` is true for security reasons.
        configuration.setAllowedOrigins(Arrays.asList(
            // Add the URL of your local development frontend here (e.g., http://localhost:3000 for React)
            "http://localhost:8081", 
            "https://your-production-frontend.com" // Add your production frontend URL here
        ));
        
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-XSRF-TOKEN"));
        
        // This is crucial for session-based authentication (cookies)
        configuration.setAllowCredentials(true);
        
        configuration.setMaxAge(3600L); // Cache pre-flight response for 1 hour
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}