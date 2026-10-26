package com.capstone.adproject.controller;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.capstone.adproject.service.EmailService;
import com.capstone.adproject.service.UserService;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @org.springframework.beans.factory.annotation.Value("${app.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    private final UserService userService;
    private final EmailService emailService;

    public AuthController(UserService userService, EmailService emailService) {
        this.userService = userService;
        this.emailService = emailService;
    }

    @GetMapping("/")
    public String redirectToLogin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && 
            !auth.getPrincipal().equals("anonymousUser")) {

            return "redirect:" + getHomePageForUser(auth);
        }
        
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String showLoginPage(
            @RequestParam(value = "error", required = false) String error, 
            Model model) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && 
            !auth.getPrincipal().equals("anonymousUser")) {
            
            logger.info("User already logged in, redirecting to home page");
            return "redirect:" + getHomePageForUser(auth);
        }
        
        if (error != null) {
            model.addAttribute("loginError", "true");
        }
        
        return "login";
    }

    private String getHomePageForUser(Authentication auth) {
        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
        
        for (GrantedAuthority authority : authorities) {
            String role = authority.getAuthority();
            
            switch (role) {
                case "ROLE_SUPER_ADMIN":
                    return "/superadmin/home";
                case "ROLE_ADMIN":
                    return "/admin/home";
                case "ROLE_STUDENT":
                    return "/student/home";
                case "ROLE_LECTURER":
                    return "/lecturer/home";
            }
        }

        return "/login";
    }
    

    @GetMapping("/forgot_password")
    public String showForgotPasswordForm() {
        return "forgot_password_form";
    }

    @PostMapping("/forgot_password")
    public String processForgotPassword(@RequestParam("email") String email, 
                                        HttpServletRequest request, 
                                        Model model) {
        
        String cleanEmail = email != null ? email.trim() : "";
        if (!cleanEmail.isBlank()) {
            Object user = userService.findUserByEmail(cleanEmail);
            if (user != null) {
                try {
                    String token = userService.generateResetToken(user);
                    
                    String baseUrl = (publicBaseUrl != null && !publicBaseUrl.isBlank())
                            ? publicBaseUrl.replaceAll("/+$", "")
                            : request.getScheme() + "://" + request.getServerName() 
                                + (request.getServerPort() == 80 || request.getServerPort() == 443 ? "" : ":" + request.getServerPort()) 
                                + request.getContextPath();
                    String resetLink = baseUrl + "/reset_password?token=" + token;

                    emailService.sendResetPasswordEmail(cleanEmail, resetLink);
                } catch (Exception e) {
                    logger.error("Error processing forgot password request for email: {}", cleanEmail, e);
                }
            }
        }
        
        model.addAttribute("message", "If an account with that email exists, a password reset link has been sent.");
        return "forgot_password_form";
    }

    @GetMapping("/reset_password")
    public String showResetPasswordForm(@RequestParam("token") String token, Model model) {
        Object user = userService.findUserByResetToken(token);
        
        if (user == null) {
            logger.warn("Password reset attempt failed: Invalid or expired token received: {}", token);
            model.addAttribute("error", "Invalid or expired password reset link.");
            return "login";
        }
        
        model.addAttribute("token", token);
        return "reset_password_form";
    }

    @PostMapping("/reset_password")
    public String processResetPassword(@RequestParam("token") String token,
                                       @RequestParam("password") String password,
                                       @RequestParam("confirm_password") String confirmPassword,
                                       Model model) {
        
        if (!password.equals(confirmPassword)) {
            model.addAttribute("token", token);
            model.addAttribute("error", "Passwords do not match.");
            return "reset_password_form";
        }

        Object user = userService.findUserByResetToken(token);
        
        if (user == null) {
            logger.error("Password reset failure: Token became invalid during submission: {}", token);
            model.addAttribute("error", "An unexpected error occurred. Please try the reset process again.");
            return "login";
        }
        
        userService.updatePassword(user, password);
        
        model.addAttribute("message", "Your password has been successfully updated. You can now login.");
        return "login";
    }

}