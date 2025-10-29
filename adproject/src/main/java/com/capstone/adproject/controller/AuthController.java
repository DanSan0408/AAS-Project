package com.capstone.adproject.controller;

import com.capstone.adproject.service.EmailService;
import com.capstone.adproject.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class AuthController {

    // Logger for debugging
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final EmailService emailService;

    // Inject the new services
    public AuthController(UserService userService, EmailService emailService) {
        this.userService = userService;
        this.emailService = emailService;
    }

    //dapatkan request utk / and then bawak ke startup.html
    @GetMapping("/")
    public String showStartupPage() {
        return "startup";
    }

    //dapatkan request utk login 
    @GetMapping("/login")
    public String showLoginPage(@RequestParam(value = "error", required = false) String error, Model model) {
        if (error != null) {
            model.addAttribute("loginError", "true");
        }
        // Check for password reset success message or token error
        if (model.containsAttribute("message") || model.containsAttribute("error")) {
            // Keep the message/error attribute if it came from a redirect
        } else if (error != null) {
            model.addAttribute("loginError", "true");
        }
        return "login";
    }
    
    // --- FORGOT PASSWORD IMPLEMENTATION ---

    /**
     * Shows the form to request a password reset email.
     */
    @GetMapping("/forgot_password")
    public String showForgotPasswordForm() {
        return "forgot_password_form"; // Create this Thymeleaf template
    }

    /**
     * Handles the submission of the forgot password form.
     */
    @PostMapping("/forgot_password")
    public String processForgotPassword(@RequestParam("email") String email, 
                                        HttpServletRequest request, 
                                        Model model) {
        
        Object user = userService.findUserByEmail(email);
        
        if (user == null) {
            model.addAttribute("error", "No user found with that email address.");
            return "forgot_password_form";
        }
        
        try {
            // 1. Generate token
            String token = userService.generateResetToken(user);
            
            // 2. Create the reset link
            // Adjust the port/context path if necessary (e.g., if behind a proxy)
            String applicationUrl = request.getScheme() + "://" + request.getServerName();
            if (request.getServerPort() != 80 && request.getServerPort() != 443) {
                applicationUrl += ":" + request.getServerPort();
            }
            String resetLink = applicationUrl + request.getContextPath() + "/reset_password?token=" + token;

            // 3. Send email
            String userEmail = email;
            emailService.sendResetPasswordEmail(userEmail, resetLink);
            
            model.addAttribute("message", "A password reset link has been sent to your email.");
            return "forgot_password_form";
            
        } catch (Exception e) {
            // Log the exception
            logger.error("Error processing forgot password request for email: {}", email, e);
            model.addAttribute("error", "Error sending email. Please try again.");
            return "forgot_password_form";
        }
    }

    /**
     * Shows the form to reset the password using the token from the email.
     */
    @GetMapping("/reset_password")
    public String showResetPasswordForm(@RequestParam("token") String token, Model model) {
        Object user = userService.findUserByResetToken(token);
        
        if (user == null) {
            logger.warn("Password reset attempt failed: Invalid or expired token received: {}", token);
            model.addAttribute("error", "Invalid or expired password reset link.");
            return "login"; // Or a dedicated error page
        }
        
        model.addAttribute("token", token);
        return "reset_password_form"; // Create this Thymeleaf template
    }

    /**
     * Handles the submission of the new password.
     */
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
        
        // Update the password and clear the token
        userService.updatePassword(user, password);
        
        model.addAttribute("message", "Your password has been successfully updated. You can now login.");
        return "login";
    }
}
