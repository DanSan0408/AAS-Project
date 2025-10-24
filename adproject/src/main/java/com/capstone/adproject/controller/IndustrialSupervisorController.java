package com.capstone.adproject.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/supervisor")
public class IndustrialSupervisorController {
    
    @GetMapping("/home")
    public String studentHome() {
        return "industrial_supervisor_home";
    }

}