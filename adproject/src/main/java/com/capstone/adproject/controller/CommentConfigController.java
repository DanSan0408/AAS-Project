package com.capstone.adproject.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.repositories.AssessmentRepository;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.CourseScopeService;

@Controller
@RequestMapping("/admin/comment-config")
public class CommentConfigController {

    @Autowired
    private AssessmentService assessmentService;
    
    @Autowired
    private AssessmentRepository assessmentRepository;

    @Autowired
    private CourseScopeService courseScopeService;
    
    private String getLoggedInUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getName() != null) {
            return authentication.getName();
        }
        return "";
    }

    private Set<Long> getManagedCourseIdsForCurrentUser() {
        return courseScopeService.getActiveCourseIdsForCurrentUser();
    }

    private boolean ownsAssessment(Assessment assessment) {
        return assessment != null
            && assessment.getCourse() != null
            && assessment.getCourse().getId() != null
            && getManagedCourseIdsForCurrentUser().contains(assessment.getCourse().getId());
    }

    private Assessment getAuthorizedAssessment(Long assessmentId) {
        Assessment assessment = assessmentService.getAssessmentById(assessmentId)
            .orElseThrow(() -> new RuntimeException("Assessment not found"));
        if (!ownsAssessment(assessment)) {
            throw new RuntimeException("Unauthorized assessment access");
        }
        return assessment;
    }

    @GetMapping("/{assessmentId}/{type}")
    public String showTypeSpecificConfig(
            @PathVariable("assessmentId") Long assessmentId,
            @PathVariable("type") String type,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        Assessment assessment;
        try {
            assessment = getAuthorizedAssessment(assessmentId);
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/home";
        }

        if (!"group".equalsIgnoreCase(type) && !"individual".equalsIgnoreCase(type)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid comment type");
            return "redirect:/admin/home";
        }
        
        boolean hasGroupRubrics = assessment.getRubrics().stream()
            .anyMatch(r -> r.getAssessmentTypes() != null && 
                          r.getAssessmentTypes().equalsIgnoreCase("Group Assessment"));
        
        boolean hasIndividualRubrics = assessment.getRubrics().stream()
            .anyMatch(r -> r.getAssessmentTypes() != null && 
                          r.getAssessmentTypes().equalsIgnoreCase("Individual Assessment"));
        
        model.addAttribute("assessment", assessment);
        model.addAttribute("configType", type.toLowerCase());
        model.addAttribute("hasGroupRubrics", hasGroupRubrics);
        model.addAttribute("hasIndividualRubrics", hasIndividualRubrics);
        model.addAttribute("adminUsername", getLoggedInUsername());
        
        return "comment_configuration_single_type";
    }

    @GetMapping("/{assessmentId}/{type}/edit/{index}")
    public String showEditSingleComment(
            @PathVariable("assessmentId") Long assessmentId,
            @PathVariable("type") String type,
            @PathVariable("index") int index,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        Assessment assessment;
        try {
            assessment = getAuthorizedAssessment(assessmentId);
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/home";
        }
        
        String label = "";
        Integer minLength = 20;
        Boolean isAnonymous = true;
        
        if ("group".equalsIgnoreCase(type)) {
            List<String> labels = assessment.getGroupCommentLabels();
            List<Integer> minLengths = assessment.getGroupCommentMinLengths();
            List<Boolean> anonymousFlags = assessment.getGroupCommentAnonymousFlags();
            
            if (index >= 0 && index < labels.size()) {
                label = labels.get(index);
                Integer configuredMinLength = (index < minLengths.size()) ? minLengths.get(index) : null;
                Boolean configuredAnonymous = (index < anonymousFlags.size()) ? anonymousFlags.get(index) : null;
                minLength = configuredMinLength != null ? configuredMinLength : 20;
                isAnonymous = configuredAnonymous != null ? configuredAnonymous : true;
            }
        } else if ("individual".equalsIgnoreCase(type)) {
            List<String> labels = assessment.getIndividualCommentLabels();
            List<Integer> minLengths = assessment.getIndividualCommentMinLengths();
            List<Boolean> anonymousFlags = assessment.getIndividualCommentAnonymousFlags();
            
            if (index >= 0 && index < labels.size()) {
                label = labels.get(index);
                Integer configuredMinLength = (index < minLengths.size()) ? minLengths.get(index) : null;
                Boolean configuredAnonymous = (index < anonymousFlags.size()) ? anonymousFlags.get(index) : null;
                minLength = configuredMinLength != null ? configuredMinLength : 20;
                isAnonymous = configuredAnonymous != null ? configuredAnonymous : true;
            }
        }
        
        model.addAttribute("assessment", assessment);
        model.addAttribute("type", type.toLowerCase());
        model.addAttribute("index", index);
        model.addAttribute("label", label);
        model.addAttribute("minLength", minLength);
        model.addAttribute("isAnonymous", isAnonymous);
        model.addAttribute("adminUsername", getLoggedInUsername());
        
        return "edit_single_comment";
    }

    @PostMapping("/{assessmentId}/{type}/edit/{index}/save")
    public String saveSingleCommentEdit(
            @PathVariable("assessmentId") Long assessmentId,
            @PathVariable("type") String type,
            @PathVariable("index") int index,
            @RequestParam("label") String label,
            @RequestParam("minLength") Integer minLength,
            @RequestParam(value = "isAnonymous", defaultValue = "false") Boolean isAnonymous,
            RedirectAttributes redirectAttributes) {
        
        try {
            Assessment assessment = getAuthorizedAssessment(assessmentId);
            
            if ("group".equalsIgnoreCase(type)) {
                List<String> labels = new ArrayList<>(assessment.getGroupCommentLabels());
                List<Integer> minLengths = new ArrayList<>(assessment.getGroupCommentMinLengths());
                List<Boolean> anonymousFlags = new ArrayList<>(assessment.getGroupCommentAnonymousFlags());
                
                if (index >= 0 && index < labels.size()) {
                    labels.set(index, label.trim());

                    while (minLengths.size() <= index) minLengths.add(20);
                    while (anonymousFlags.size() <= index) anonymousFlags.add(true);
                    
                    minLengths.set(index, Math.max(10, Math.min(500, minLength)));
                    anonymousFlags.set(index, isAnonymous);
                    
                    assessment.setGroupCommentLabels(labels);
                    assessment.setGroupCommentMinLengths(minLengths);
                    assessment.setGroupCommentAnonymousFlags(anonymousFlags);
                }
            } else if ("individual".equalsIgnoreCase(type)) {
                List<String> labels = new ArrayList<>(assessment.getIndividualCommentLabels());
                List<Integer> minLengths = new ArrayList<>(assessment.getIndividualCommentMinLengths());
                List<Boolean> anonymousFlags = new ArrayList<>(assessment.getIndividualCommentAnonymousFlags());
                
                if (index >= 0 && index < labels.size()) {
                    labels.set(index, label.trim());

                    while (minLengths.size() <= index) minLengths.add(20);
                    while (anonymousFlags.size() <= index) anonymousFlags.add(true);
                    
                    minLengths.set(index, Math.max(10, Math.min(500, minLength)));
                    anonymousFlags.set(index, isAnonymous);
                    
                    assessment.setIndividualCommentLabels(labels);
                    assessment.setIndividualCommentMinLengths(minLengths);
                    assessment.setIndividualCommentAnonymousFlags(anonymousFlags);
                }
            }
            
            if (assessment != null) {
                assessmentRepository.save(assessment);
            }
            
            redirectAttributes.addFlashAttribute("successMessage", "Comment question updated successfully!");
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating comment: " + e.getMessage());
        }
        
        return "redirect:/admin/home";
    }

    @PostMapping("/{assessmentId}/delete-comment")
    public String deleteComment(
            @PathVariable("assessmentId") Long assessmentId,
            @RequestParam("type") String type,
            @RequestParam("index") int index,
            RedirectAttributes redirectAttributes) {
        
        try {
            Assessment assessment = getAuthorizedAssessment(assessmentId);
            
            if ("group".equalsIgnoreCase(type)) {
                List<String> labels = new ArrayList<>(assessment.getGroupCommentLabels());
                List<Integer> minLengths = new ArrayList<>(assessment.getGroupCommentMinLengths());
                List<Boolean> anonymousFlags = new ArrayList<>(assessment.getGroupCommentAnonymousFlags());
                
                if (index < labels.size()) {
                    labels.remove(index);
                    if (index < minLengths.size()) minLengths.remove(index);
                    if (index < anonymousFlags.size()) anonymousFlags.remove(index);
                    
                    assessment.setGroupCommentLabels(labels);
                    assessment.setGroupCommentMinLengths(minLengths);
                    assessment.setGroupCommentAnonymousFlags(anonymousFlags);
                    assessment.setGroupCommentCount(labels.size());
                }
            } else if ("individual".equalsIgnoreCase(type)) {
                List<String> labels = new ArrayList<>(assessment.getIndividualCommentLabels());
                List<Integer> minLengths = new ArrayList<>(assessment.getIndividualCommentMinLengths());
                List<Boolean> anonymousFlags = new ArrayList<>(assessment.getIndividualCommentAnonymousFlags());
                
                if (index < labels.size()) {
                    labels.remove(index);
                    if (index < minLengths.size()) minLengths.remove(index);
                    if (index < anonymousFlags.size()) anonymousFlags.remove(index);
                    
                    assessment.setIndividualCommentLabels(labels);
                    assessment.setIndividualCommentMinLengths(minLengths);
                    assessment.setIndividualCommentAnonymousFlags(anonymousFlags);
                    assessment.setIndividualCommentCount(labels.size());
                }
            }
            
            if (assessment != null) {
                assessmentRepository.save(assessment);
            }
            
            redirectAttributes.addFlashAttribute("successMessage", "Comment question deleted successfully!");
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting comment: " + e.getMessage());
        }
        
        return "redirect:/admin/home";
    }
    
    @PostMapping("/{assessmentId}/{type}/save")
    public String saveTypeSpecificConfig(
            @PathVariable("assessmentId") Long assessmentId,
            @PathVariable("type") String type,
            @RequestParam(value = "commentLabels", required = false) List<String> commentLabels,
            @RequestParam(value = "commentMinLengths", required = false) List<Integer> commentMinLengths,
            @RequestParam(value = "commentAnonymous", required = false) List<String> commentAnonymousFlags,
            RedirectAttributes redirectAttributes) {
        
        try {
            Assessment assessment = getAuthorizedAssessment(assessmentId);
            
            List<String> processedLabels = new ArrayList<>();
            List<Integer> processedMinLengths = new ArrayList<>();
            List<Boolean> processedAnonymousFlags = new ArrayList<>();
            
            if (commentLabels != null && !commentLabels.isEmpty()) {
                for (int i = 0; i < commentLabels.size(); i++) {
                    String label = commentLabels.get(i);
                    if (label == null || label.trim().isEmpty()) continue;
                    
                    processedLabels.add(label.trim());
                    
                    Integer minLength = 20;
                    if (commentMinLengths != null && i < commentMinLengths.size() && commentMinLengths.get(i) != null) {
                        minLength = commentMinLengths.get(i);
                        if (minLength < 10) minLength = 10;
                        if (minLength > 500) minLength = 500;
                    }
                    processedMinLengths.add(minLength);
                    
                    Boolean isAnonymous = (commentAnonymousFlags != null && 
                                          commentAnonymousFlags.size() > i && 
                                          "true".equals(commentAnonymousFlags.get(i)));
                    processedAnonymousFlags.add(isAnonymous);
                }
            }
            
            if (processedLabels.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "Please configure at least one comment question.");
                return "redirect:/admin/comment-config/" + assessmentId + "/" + type;
            }
            
            if ("group".equalsIgnoreCase(type)) {
                assessment.setGroupCommentCount(processedLabels.size());
                assessment.setGroupCommentLabels(processedLabels);
                assessment.setGroupCommentMinLengths(processedMinLengths);
                assessment.setGroupCommentAnonymousFlags(processedAnonymousFlags);
            } else if ("individual".equalsIgnoreCase(type)) {
                assessment.setIndividualCommentCount(processedLabels.size());
                assessment.setIndividualCommentLabels(processedLabels);
                assessment.setIndividualCommentMinLengths(processedMinLengths);
                assessment.setIndividualCommentAnonymousFlags(processedAnonymousFlags);
            }
            
            if (assessment != null) {
                assessmentRepository.save(assessment);
            }
            
            String successMsg = type.substring(0, 1).toUpperCase() + type.substring(1) + 
                               " comment configuration saved successfully! " + processedLabels.size() + " question(s).";
            
            redirectAttributes.addFlashAttribute("successMessage", successMsg);
            return "redirect:/admin/home";
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error saving comment configuration: " + e.getMessage());
            return "redirect:/admin/comment-config/" + assessmentId + "/" + type;
        }
    }
}