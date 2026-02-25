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
        //authenticate and authorize
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
            )//login
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
}