package com.capstone.adproject.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class LogoutController {

    @PostMapping("/logout")
    public String performLogout(HttpServletRequest request, HttpServletResponse response) {
        // This method will be triggered if the Spring Security filter chain is bypassed.
        // It's a simple, explicit way to ensure the logout URL is mapped.
        // It's a temporary workaround for the persistent error you are seeing.

        // In a real application, you would perform the logout logic here,
        // but for now, we just want to ensure the URL is being reached.
        System.out.println("Logout request received at @PostMapping /logout.");

        // We can force a redirect to the startup page.
        return "redirect:/startup";
    }
}
