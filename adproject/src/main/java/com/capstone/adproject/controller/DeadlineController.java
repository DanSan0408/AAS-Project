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
import org.springframework.web.bind.annotation.RequestParam; // ⭐ Import new annotation
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

    // ⭐ MODIFIED saveDeadline method
    @PostMapping("/save")
    public String saveDeadline(
        @ModelAttribute Deadline deadline, 
        @RequestParam(value = "confirmDuplicate", required = false, defaultValue = "false") boolean confirmDuplicate, 
        RedirectAttributes redirectAttributes) {
        
        String title = deadline.getTitle() != null ? deadline.getTitle().trim() : "";
        
        if (title.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Title cannot be empty!");
            return "redirect:/admin/home";
        }

        // 1. Check for Duplicate Title (only if it's a NEW deadline or an update changing the title)
        Optional<Deadline> existingDeadline = deadlineService.findByTitle(title);

        if (existingDeadline.isPresent()) {
            // Check if the duplicate belongs to a different entity (i.e., it's a true duplicate or a new entry)
            boolean isUpdateOfSameEntity = deadline.getId() != null && 
                                           existingDeadline.get().getId().equals(deadline.getId());

            if (!isUpdateOfSameEntity) {
                if (!confirmDuplicate) {
                    // Title exists and admin has NOT confirmed: trigger the warning
                    redirectAttributes.addFlashAttribute("deadlineToSave", deadline); // Pass the entire object
                    redirectAttributes.addFlashAttribute("isDuplicate", true);
                    return "redirect:/admin/home"; 
                }
                // If confirmDuplicate is true, we proceed to save (Step 2).
            }
        }

        // 2. Save the Deadline (If not duplicate, or if duplicate confirmed)
        deadlineService.save(deadline);
        redirectAttributes.addFlashAttribute("successMessage", "Deadline saved successfully!");
        
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
        // You might want to add duplicate check logic here too, but for simplicity, 
        // we'll keep the confirmation logic in the /save method for now.
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