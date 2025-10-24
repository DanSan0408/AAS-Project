package com.capstone.adproject.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/lecturer")
public class LecturerController {
    
    @GetMapping("/home")
    public String studentHome() {
        return "lecturer_home";
    }
}