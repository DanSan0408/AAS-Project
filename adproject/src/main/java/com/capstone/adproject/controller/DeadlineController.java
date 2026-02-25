package com.capstone.adproject.controller;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
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
public String saveDeadline(
    @ModelAttribute Deadline deadline, 
    @RequestParam(value = "confirmDuplicate", required = false, defaultValue = "false") boolean confirmDuplicate, 
    RedirectAttributes redirectAttributes) {
    
    String title = deadline.getTitle() != null ? deadline.getTitle().trim() : "";
    
    if (title.isEmpty()) {
        redirectAttributes.addFlashAttribute("errorMessage", "Title cannot be empty!");
        return "redirect:/admin/home";
    }

    if (!confirmDuplicate) {
        boolean isDuplicate = deadlineService.isTitleDuplicateIgnoringWhitespace(title, deadline.getId());
        
        if (isDuplicate) {
            // Find the existing deadline with similar title for display
            String existingTitle = findExistingTitleIgnoringWhitespace(title, deadline.getId());
            
            redirectAttributes.addFlashAttribute("deadlineToSave", deadline);
            redirectAttributes.addFlashAttribute("isDuplicate", true);
            redirectAttributes.addFlashAttribute("existingTitle", existingTitle); // Pass existing title for display
            return "redirect:/admin/home"; 
        }
    }

    deadlineService.save(deadline);
    redirectAttributes.addFlashAttribute("successMessage", "Deadline saved successfully!");
    
    return "redirect:/admin/home";
}

private String findExistingTitleIgnoringWhitespace(String title, Long excludeId) {
    String normalizedTitle = title.replaceAll("\\s+", "").toLowerCase();
    List<Deadline> allDeadlines = deadlineService.getAllDeadlines();
    
    for (Deadline deadline : allDeadlines) {
        if (excludeId != null && deadline.getId().equals(excludeId)) {
            continue;
        }
        if (deadline.getTitle() != null) {
            String normalizedExisting = deadline.getTitle().replaceAll("\\s+", "").toLowerCase();
            if (normalizedExisting.equals(normalizedTitle)) {
                return deadline.getTitle();
            }
        }
    }
    return title;
}
    @GetMapping("/edit/{id}")
    public String editDeadline(@PathVariable("id") Long id, Model model) {
        Optional<Deadline> deadlineOptional = deadlineService.getDeadlineById(id);
        if (deadlineOptional.isPresent()) {
            model.addAttribute("deadline", deadlineOptional.get());
            return "edit-deadline"; 
        } else {
            return "redirect:/admin/home";
        }
    }

    @PostMapping("/update")
    public String updateDeadline(@ModelAttribute Deadline deadline, RedirectAttributes redirectAttributes) {
        deadlineService.save(deadline);
        redirectAttributes.addFlashAttribute("successMessage", "Deadline updated successfully!");
        return "redirect:/admin/home";
    }

    @PostMapping("/delete/{id}") 
public String deleteDeadline(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
    deadlineService.deleteDeadline(id);
    redirectAttributes.addFlashAttribute("successMessage", "Deadline deleted successfully!");
    return "redirect:/admin/home";
}
}