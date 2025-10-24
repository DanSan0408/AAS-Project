package com.capstone.adproject.config;

import java.io.IOException;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CustomAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    //IOE exception : input/output problem
    //Servlet exception : servlet operation (server handling like client requests/generate dynamic responses) problem
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        
        // AuthorityUtils == helper class in SpringSecurity
        //authorityListToSet == convert list of GrantedAuthority objects into set of strings
        Set<String> roles = AuthorityUtils.authorityListToSet(authentication.getAuthorities()); //getAuthorities -> return a collection of GrantedAuthority objects (roles/authorities to a user)

        // nak hantar user ke page yang betul
        if (roles.contains("ROLE_ADMIN")) {
            // Redirect to admin_home
            response.sendRedirect("/admin/home");

        } else if (roles.contains("ROLE_STUDENT")) {
            // Redirect to student_home
            response.sendRedirect("/student/home");

        } else if (roles.contains("ROLE_LECTURER")) {
            // Redirect to lecturer_home
            response.sendRedirect("/lecturer/home");

        } else if (roles.contains("ROLE_SUPERVISOR")) {
            // Redirect to industrial_supervisor_home
            response.sendRedirect("/supervisor/home");

        } else {
            // Felse, hantar balik ke page startup.html
            response.sendRedirect("/");
        }
    }
}
