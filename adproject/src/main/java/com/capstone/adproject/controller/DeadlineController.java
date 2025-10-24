package com.capstone.adproject.controller;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.capstone.adproject.model.Deadline;
import com.capstone.adproject.service.DeadlineService;

@Controller
@RequestMapping("/deadlines")
public class DeadlineController {

    private final DeadlineService deadlineService;

    @Autowired
    public DeadlineController(DeadlineService deadlineService) {
        this.deadlineService = deadlineService;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        dateFormat.setLenient(false);
        binder.registerCustomEditor(Date.class, new CustomDateEditor(dateFormat, true));
    }

    @PostMapping("/save")
    public String saveDeadline(@ModelAttribute Deadline deadline, RedirectAttributes redirectAttributes) {
        if (deadline.getTitle() != null && !deadline.getTitle().trim().isEmpty()) {
            deadlineService.save(deadline);
            redirectAttributes.addFlashAttribute("successMessage", "Deadline saved successfully!");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Title cannot be empty!");
        }
        return "redirect:/admin/home";
    }

    @GetMapping("/edit/{id}")
    public String editDeadline(@PathVariable("id") Long id, Model model) {
        Optional<Deadline> deadlineOptional = deadlineService.getDeadlineById(id);
        if (deadlineOptional.isPresent()) {
            model.addAttribute("deadline", deadlineOptional.get());
            return "edit-deadline"; // Ensure you have this template for editing
        } else {
            // Handle case where deadline is not found
            return "redirect:/admin/home";
        }
    }

    @PostMapping("/update")
    public String updateDeadline(@ModelAttribute Deadline deadline, RedirectAttributes redirectAttributes) {
        deadlineService.save(deadline);
        redirectAttributes.addFlashAttribute("successMessage", "Deadline updated successfully!");
        return "redirect:/admin/home";
    }

    @GetMapping("/delete/{id}")
    public String deleteDeadline(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        deadlineService.deleteDeadline(id);
        redirectAttributes.addFlashAttribute("successMessage", "Deadline deleted successfully!");
        return "redirect:/admin/home";
    }

    
}