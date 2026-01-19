package com.capstone.adproject.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices.RememberMeTokenAlgorithm;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;

import com.capstone.adproject.service.CustomUserDetailsService;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    // ✅ FIXED: Use a constant key that won't change on restart
    private static final String REMEMBER_ME_KEY = "utm-cas-remember-me-secret-key-2025-DO-NOT-CHANGE";

    public SecurityConfig(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Autowired
    private AuthenticationSuccessHandler authenticationSuccessHandler;

    /**
     * ✅ RequestCache bean to save and restore user's last visited page
     */
    @Bean
    public RequestCache requestCache() {
        return new HttpSessionRequestCache();
    }

    /**
     * ✅ CRITICAL: Create RememberMeServices bean with FIXED key
     */
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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authenticationProvider(daoAuthenticationProvider())
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/", "/login", "/css/**", "/js/**", "/images/**", 
                    "/forgot_password", "/reset_password/**", "/debug/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/rubrics/**").hasRole("ADMIN")
                .requestMatchers("/student/**", "/student/assessment/**").hasRole("STUDENT")
                .requestMatchers("/lecturer/**").hasRole("LECTURER")
                .requestMatchers("/supervisor/**").hasRole("SUPERVISOR")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(authenticationSuccessHandler)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            
            // ✅ Use the RememberMeServices bean
            .rememberMe(rememberMe -> rememberMe
                .rememberMeServices(rememberMeServices())
            )
            
            // ✅ Configure request cache to save last visited page
            .requestCache(cache -> cache
                .requestCache(requestCache())
            )
            
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .deleteCookies("JSESSIONID", "utm-cas-remember-me")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .permitAll()
            );

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setPasswordEncoder(passwordEncoder());
        provider.setUserDetailsService(userDetailsService);
        return provider;
    }
}