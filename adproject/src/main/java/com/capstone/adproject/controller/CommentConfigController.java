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
     * NOW SUPPORTS SEPARATE GROUP AND INDIVIDUAL CONFIGURATIONS
     */
    @GetMapping("/{assessmentId}")
    public String showCommentConfig(@PathVariable Long assessmentId, Model model, RedirectAttributes redirectAttributes) {
        
        Assessment assessment = assessmentService.getAssessmentById(assessmentId)
                .orElseThrow(() -> new RuntimeException("Assessment not found"));
        
        // Check which rubric types exist
        boolean hasGroupRubrics = assessment.getRubrics().stream()
            .anyMatch(r -> r.getAssessmentTypes() != null && 
                          r.getAssessmentTypes().equalsIgnoreCase("Group Assessment"));
        
        boolean hasIndividualRubrics = assessment.getRubrics().stream()
            .anyMatch(r -> r.getAssessmentTypes() != null && 
                          r.getAssessmentTypes().equalsIgnoreCase("Individual Assessment"));
        
        model.addAttribute("assessment", assessment);
        model.addAttribute("hasGroupRubrics", hasGroupRubrics);
        model.addAttribute("hasIndividualRubrics", hasIndividualRubrics);
        model.addAttribute("adminUsername", getLoggedInUsername());
        
        return "comment_configuration";
    }
    
    /**
     * Save comment configuration for an assessment
     * NOW HANDLES SEPARATE GROUP AND INDIVIDUAL CONFIGURATIONS
     */
    @PostMapping("/{assessmentId}/save")
    public String saveCommentConfig(
            @PathVariable Long assessmentId,
            // Group Assessment Parameters
            @RequestParam(value = "groupCommentLabels", required = false) List<String> groupCommentLabels,
            @RequestParam(value = "groupCommentMinLengths", required = false) List<Integer> groupCommentMinLengths,
            @RequestParam(value = "groupCommentAnonymous", required = false) List<String> groupCommentAnonymousFlags,
            // Individual Assessment Parameters
            @RequestParam(value = "individualCommentLabels", required = false) List<String> individualCommentLabels,
            @RequestParam(value = "individualCommentMinLengths", required = false) List<Integer> individualCommentMinLengths,
            @RequestParam(value = "individualCommentAnonymous", required = false) List<String> individualCommentAnonymousFlags,
            RedirectAttributes redirectAttributes) {
        
        try {
            Assessment assessment = assessmentService.getAssessmentById(assessmentId)
                    .orElseThrow(() -> new RuntimeException("Assessment not found"));
            
            // Process GROUP assessment comments
            List<String> processedGroupLabels = new ArrayList<>();
            List<Integer> processedGroupMinLengths = new ArrayList<>();
            
            if (groupCommentLabels != null && !groupCommentLabels.isEmpty()) {
                for (int i = 0; i < groupCommentLabels.size(); i++) {
                    String label = groupCommentLabels.get(i);
                    
                    // Skip empty labels
                    if (label == null || label.trim().isEmpty()) {
                        continue;
                    }
                    
                    processedGroupLabels.add(label.trim());
                    
                    // Get minLength for this comment (default to 20 if not provided)
                    Integer minLength = 20;
                    if (groupCommentMinLengths != null && i < groupCommentMinLengths.size() && groupCommentMinLengths.get(i) != null) {
                        minLength = groupCommentMinLengths.get(i);
                        
                        // Validate minLength
                        if (minLength < 10) {
                            minLength = 10;
                        }
                        if (minLength > 500) {
                            minLength = 500;
                        }
                    }
                    processedGroupMinLengths.add(minLength);
                }
            }
            
            // Process INDIVIDUAL assessment comments
            List<String> processedIndividualLabels = new ArrayList<>();
            List<Integer> processedIndividualMinLengths = new ArrayList<>();
            
            if (individualCommentLabels != null && !individualCommentLabels.isEmpty()) {
                for (int i = 0; i < individualCommentLabels.size(); i++) {
                    String label = individualCommentLabels.get(i);
                    
                    // Skip empty labels
                    if (label == null || label.trim().isEmpty()) {
                        continue;
                    }
                    
                    processedIndividualLabels.add(label.trim());
                    
                    // Get minLength for this comment (default to 20 if not provided)
                    Integer minLength = 20;
                    if (individualCommentMinLengths != null && i < individualCommentMinLengths.size() && individualCommentMinLengths.get(i) != null) {
                        minLength = individualCommentMinLengths.get(i);
                        
                        // Validate minLength
                        if (minLength < 10) {
                            minLength = 10;
                        }
                        if (minLength > 500) {
                            minLength = 500;
                        }
                    }
                    processedIndividualMinLengths.add(minLength);
                }
            }
            
            // Validate that at least one type has comments configured
            if (processedGroupLabels.isEmpty() && processedIndividualLabels.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "Please configure at least one comment question for either Group or Individual assessments.");
                return "redirect:/admin/comment-config/" + assessmentId;
            }
            
            // ===== SET GROUP ASSESSMENT COMMENT CONFIG =====
            assessment.setGroupCommentCount(processedGroupLabels.size());
            assessment.setGroupCommentLabels(processedGroupLabels);
            
            if (!processedGroupMinLengths.isEmpty()) {
                assessment.setGroupCommentMinLength(processedGroupMinLengths.get(0));
            }
            
            Boolean groupIsAnonymous = (groupCommentAnonymousFlags != null && !groupCommentAnonymousFlags.isEmpty());
            assessment.setGroupCommentsAnonymous(groupIsAnonymous);
            
            // ===== SET INDIVIDUAL ASSESSMENT COMMENT CONFIG =====
            assessment.setIndividualCommentCount(processedIndividualLabels.size());
            assessment.setIndividualCommentLabels(processedIndividualLabels);
            
            if (!processedIndividualMinLengths.isEmpty()) {
                assessment.setIndividualCommentMinLength(processedIndividualMinLengths.get(0));
            }
            
            Boolean individualIsAnonymous = (individualCommentAnonymousFlags != null && !individualCommentAnonymousFlags.isEmpty());
            assessment.setIndividualCommentsAnonymous(individualIsAnonymous);
            
            // Save using repository
            assessmentRepository.save(assessment);
            
            String successMsg = "Comment configuration saved successfully! ";
            if (processedGroupLabels.size() > 0) {
                successMsg += processedGroupLabels.size() + " Group comment question(s). ";
            }
            if (processedIndividualLabels.size() > 0) {
                successMsg += processedIndividualLabels.size() + " Individual comment question(s).";
            }
            
            redirectAttributes.addFlashAttribute("successMessage", successMsg);
            
            return "redirect:/admin/home";
            
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error saving comment configuration: " + e.getMessage());
            return "redirect:/admin/comment-config/" + assessmentId;
        }
    }
}