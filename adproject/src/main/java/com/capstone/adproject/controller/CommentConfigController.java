package com.capstone.adproject.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.capstone.adproject.model.Admin;
import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.repositories.AssessmentRepository;
import com.capstone.adproject.service.AssessmentService;

@Controller
@RequestMapping("/admin/comment-config")
public class CommentConfigController {

    @Autowired
    private AssessmentService assessmentService;
    
    @Autowired
    private AssessmentRepository assessmentRepository;
    
    private String getLoggedInUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Admin) {
            return ((Admin) authentication.getPrincipal()).getUsername();
        }
        return "Admin";
    }

    /**
     * Show comment configuration page for an assessment
     */
    @GetMapping("/{assessmentId}")
    public String showCommentConfig(@PathVariable Long assessmentId, Model model, RedirectAttributes redirectAttributes) {
        
        Assessment assessment = assessmentService.getAssessmentById(assessmentId)
                .orElseThrow(() -> new RuntimeException("Assessment not found"));
        
        model.addAttribute("assessment", assessment);
        model.addAttribute("adminUsername", getLoggedInUsername());
        
        return "comment_configuration";
    }
    
    /**
     * Save comment configuration for an assessment
     * NEW: Handles arrays for individual comment settings
     */
    @PostMapping("/{assessmentId}/save")
    public String saveCommentConfig(
            @PathVariable Long assessmentId,
            @RequestParam(value = "commentLabels", required = false) List<String> commentLabels,
            @RequestParam(value = "commentMinLengths", required = false) List<Integer> commentMinLengths,
            @RequestParam(value = "commentAnonymous", required = false) List<String> commentAnonymousFlags,
            RedirectAttributes redirectAttributes) {
        
        try {
            Assessment assessment = assessmentService.getAssessmentById(assessmentId)
                    .orElseThrow(() -> new RuntimeException("Assessment not found"));
            
            // Validate inputs
            if (commentLabels == null || commentLabels.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "Please add at least one comment question.");
                return "redirect:/admin/comment-config/" + assessmentId;
            }
            
            // Process labels - remove empty ones
            List<String> processedLabels = new ArrayList<>();
            List<Integer> processedMinLengths = new ArrayList<>();
            
            for (int i = 0; i < commentLabels.size(); i++) {
                String label = commentLabels.get(i);
                
                // Skip empty labels
                if (label == null || label.trim().isEmpty()) {
                    continue;
                }
                
                processedLabels.add(label.trim());
                
                // Get minLength for this comment (default to 20 if not provided)
                Integer minLength = 20;
                if (commentMinLengths != null && i < commentMinLengths.size() && commentMinLengths.get(i) != null) {
                    minLength = commentMinLengths.get(i);
                    
                    // Validate minLength
                    if (minLength < 10) {
                        minLength = 10;
                    }
                    if (minLength > 500) {
                        minLength = 500;
                    }
                }
                processedMinLengths.add(minLength);
            }
            
            // Validate that we have at least one valid question
            if (processedLabels.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "Please add at least one valid comment question.");
                return "redirect:/admin/comment-config/" + assessmentId;
            }
            
            // Set commentCount
            assessment.setCommentCount(processedLabels.size());
            
            // Set commentLabels
            assessment.setCommentLabels(processedLabels);
            
            // For now, we'll use the FIRST comment's minLength as the global setting
            // (This maintains backward compatibility with existing form)
            assessment.setCommentMinLength(processedMinLengths.get(0));
            
            // For anonymous setting: if ANY checkbox is checked, set to true
            // Note: HTML checkboxes only send values when checked
            Boolean isAnonymous = (commentAnonymousFlags != null && !commentAnonymousFlags.isEmpty());
            assessment.setCommentsAnonymous(isAnonymous);
            
            // Save using repository
            assessmentRepository.save(assessment);
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Comment configuration saved successfully! " + 
                processedLabels.size() + " comment question(s) configured.");
            
            return "redirect:/admin/home";
            
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error saving comment configuration: " + e.getMessage());
            return "redirect:/admin/comment-config/" + assessmentId;
        }
    }
}