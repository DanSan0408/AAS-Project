package com.capstone.adproject.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class LogoutController {

    @PostMapping("/logout")
    public String performLogout(HttpServletRequest request, HttpServletResponse response) {


        System.out.println("Logout request received at @PostMapping /logout.");

        return "redirect:/startup";
    }
}
