package com.capstone.adproject.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
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
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.capstone.adproject.service.CustomUserDetailsService;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;


@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    //untuk cookie, generate a token in the browser and di-hash guna secret key ni
    private static final String REMEMBER_ME_KEY = "utm-cas-remember-me-secret-key-2025-DO-NOT-CHANGE";

    public SecurityConfig(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    //encrypt password guna BCrypt
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Autowired
    private AuthenticationSuccessHandler authenticationSuccessHandler;

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
                REMEMBER_ME_KEY, 
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
                .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                .requestMatchers(HttpMethod.OPTIONS).permitAll()
                .requestMatchers("/", "/login", "/css/**", "/js/**", "/images/**",
                    "/forgot_password", "/reset_password/**", "/csp-violations", "/error", "/error/**").permitAll()
                .requestMatchers("/superadmin/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN") // Super Admin can access admin pages
                .requestMatchers("/rubrics/**").hasAnyRole("ADMIN", "SUPER_ADMIN") // Super Admin can access rubric pages
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
                    String policy = "default-src 'self'; " +
                                    "script-src 'self' 'nonce-{nonce}'; " +
                                    "style-src 'self' 'unsafe-inline'; " + // Temporarily allow inline styles to focus on scripts
                                    "img-src 'self' data:; " +
                                    "font-src 'self'; " +
                                    "connect-src 'self'; " +
                                    "frame-src 'none'; object-src 'none'; " +
                                    "report-uri /csp-violations;";
                    csp.policyDirectives(policy);
                })
            );

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

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.UNAUTHORIZED.value());
            request.setAttribute(RequestDispatcher.ERROR_MESSAGE, authException.getMessage());
            request.getRequestDispatcher("/error").forward(request, response);
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.FORBIDDEN.value());
            request.setAttribute(RequestDispatcher.ERROR_MESSAGE, accessDeniedException.getMessage());
            request.getRequestDispatcher("/error").forward(request, response);
        };
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