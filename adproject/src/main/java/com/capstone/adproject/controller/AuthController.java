package com.capstone.adproject.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    //dapatkan request utk / and then bawak ke startup.html
    @GetMapping("/")
    public String showStartupPage() {
        return "startup";
    }

    //dapatkan request utk login 
    @GetMapping("/login")
    //@RequestParam : bind Servlet request parameters to a method argument in a controller
    //check login parameters sama tak dengan dalam controller --> database
    public String showLoginPage(@RequestParam(value = "error", required = false) String error, Model model) {
        if (error != null) {
            model.addAttribute("loginError", "true");
        }
        return "login";
    }
}
