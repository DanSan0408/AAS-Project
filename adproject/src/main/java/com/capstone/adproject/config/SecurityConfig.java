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

import com.capstone.adproject.service.CustomUserDetailsService;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    //constructor - utk bagi kita guna CustomAuthenticationSuccessHandler utk determine role nnt
    public SecurityConfig(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    // utk encrypt/decrypt password user (hashing and verification)
    // even if ada 2 user password sama dia akan letak random salt so hashes will look different
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Autowired
    private AuthenticationSuccessHandler authenticationSuccessHandler;

    //give permission to the website to access this files.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authenticationProvider(daoAuthenticationProvider())
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/", "/startup", "/login", "/css/**", "/js/**", "/images/**", "/forgot_password", "/reset_password/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/rubrics/**").hasRole("ADMIN")
                .requestMatchers("/student/**").hasRole("STUDENT")
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

            // --- START: REMEMBER ME CONFIGURATION ---
            // The illegal characters (non-breaking spaces) causing the compile errors have been removed.
            .rememberMe(rememberMe -> rememberMe
                .key("aUniqueAndSecureKeyForRememberMe") // IMPORTANT: Change this to a unique, secret key
                .tokenValiditySeconds(604800) // 7 days (7 * 24 * 60 * 60 = 604800 seconds)
                .userDetailsService(userDetailsService) // Essential for loading the user from the remember-me token
            )
            // --- END: REMEMBER ME CONFIGURATION ---

            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/startup")
                .permitAll()
            );

        return http.build();
    }

    //DAO = Data Access Object (built in authentication provider that uses database to authenticate user)
    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        //check raw password user enter sama tak dengan hash password dalam database
        provider.setPasswordEncoder(passwordEncoder());
        //bagi spring security guna custom UserDetailsService utk loads user details. 
        provider.setUserDetailsService(userDetailsService);
        return provider;
    }
}
