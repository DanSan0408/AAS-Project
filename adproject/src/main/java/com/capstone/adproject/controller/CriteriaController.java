package com.capstone.adproject.controller;

import java.util.ArrayList; 
import java.util.Arrays;
import java.util.List; 
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Criteria;
import com.capstone.adproject.model.CriteriaRating; 
import com.capstone.adproject.service.CriteriaService;
import com.capstone.adproject.service.RubricService;

@Controller
@RequestMapping("/criteria") 
public class CriteriaController {

    private final CriteriaService criteriaService;
    private final RubricService rubricService; 

    // Helper method to get the hardcoded criteria rating data
    private static CriteriaRating createDefaultRating(int level, Criteria criteria) {
        CriteriaRating rating = new CriteriaRating();
        rating.setCriteria(criteria);
        rating.setLevel(level); // Set the new 'level' field
        rating.setRatingMark((double) level); // Set mark equal to level (0.0 to 4.0)

        switch (level) {
            case 0:
                rating.setName("Never");
                rating.setDescription("(0% of the time)");
                break;
            case 1:
                rating.setName("Rarely");
                rating.setDescription("(25% of the time)");
                break;
            case 2:
                rating.setName("Sometimes");
                rating.setDescription("(50% of the time)");
                break;
            case 3:
                rating.setName("Frequently");
                rating.setDescription("(75% of the time)");
                break;
            case 4:
                rating.setName("Everytime");
                rating.setDescription("(100% of the time)");
                break;
            default:
                rating.setName("N/A");
                rating.setDescription("Invalid Level");
        }
        return rating;
    }

    public CriteriaController(CriteriaService criteriaService, RubricService rubricService) {
        this.criteriaService = criteriaService;
        this.rubricService = rubricService;
    }

    @GetMapping("/add/{assessmentId}")
    public String showAddCriteriaForm(@PathVariable Long assessmentId, Model model) {
        Assessment assessment = rubricService.findAssessmentById(assessmentId);
        Criteria criteria = new Criteria();
        criteria.setAssessment(assessment);
        
        // --- MODIFIED: Initialize with all five CriteriaRating levels (0 to 4) ---
        List<CriteriaRating> initialRatings = new ArrayList<>();
        for (int i = 0; i <= 4; i++) {
            initialRatings.add(createDefaultRating(i, criteria));
        }
        criteria.setCriteriaRatings(initialRatings);
        // --------------------------------------------------------------------------
        
        model.addAttribute("evaluationTypes", new String[]{"Group", "Individual"});
        model.addAttribute("assessmentTypesOptions", new String[]{"Individual Assessment", "Group Assessment"});
        model.addAttribute("criteria", criteria);
        model.addAttribute("pageTitle", "Add New Criteria Rubric");
        model.addAttribute("formAction", "/criteria/save");
        model.addAttribute("targetFormUrl", "/rubrics/add/" + assessmentId); 
        return "criteria-form"; 
    }

    @GetMapping("/edit/{criteriaId}")
    @Transactional 
    public String showEditCriteriaForm(@PathVariable Long criteriaId, Model model) {
        Criteria criteria = criteriaService.findCriteriaById(criteriaId);
        Long assessmentId = criteria.getAssessment().getId();

        // --- MODIFIED: Ensure exactly 5 ratings exist and are correctly initialized ---
        List<CriteriaRating> existingRatings = criteria.getCriteriaRatings();
        
        // If the list is not 5, or is null, we re-initialize and sync.
        if (existingRatings == null || existingRatings.size() != 5) {
            List<CriteriaRating> newRatings = new ArrayList<>();
            for (int i = 0; i <= 4; i++) {
                
                // Try to find an existing rating by its mark/level to preserve any primary key/ID
                final int level = i;
                CriteriaRating ratingToUse = existingRatings.stream()
                    .filter(r -> r.getLevel() != null && r.getLevel().equals(level))
                    .findFirst()
                    .orElseGet(() -> createDefaultRating(level, criteria)); // Create new if not found
                
                // If it was an existing, ensure its defaults match the required structure
                if (ratingToUse.getId() != null) {
                    // Update/Set defaults (important for criteria created before this change)
                    CriteriaRating defaults = createDefaultRating(level, criteria);
                    ratingToUse.setName(defaults.getName());
                    ratingToUse.setDescription(defaults.getDescription());
                    ratingToUse.setRatingMark(defaults.getRatingMark());
                    ratingToUse.setLevel(defaults.getLevel());
                }
                
                ratingToUse.setCriteria(criteria);
                newRatings.add(ratingToUse);
            }
            // Sort by level before setting to maintain indexed binding in Thymeleaf forms
            newRatings.sort((r1, r2) -> Integer.compare(r1.getLevel(), r2.getLevel()));
            criteria.setCriteriaRatings(newRatings); 
        } else {
             // Ensure bi-directional link and level/mark are set for existing ratings
             for (CriteriaRating rating : criteria.getCriteriaRatings()) {
                 rating.setCriteria(criteria);
                 // Optional: Re-sync mark/level if form binding changed it unexpectedly
                 if (rating.getLevel() != null && !rating.getRatingMark().equals((double) rating.getLevel())) {
                    rating.setRatingMark((double) rating.getLevel());
                 }
             }
             // Ensure sorting for reliable form display
             criteria.getCriteriaRatings().sort((r1, r2) -> Integer.compare(r1.getLevel(), r2.getLevel()));
        }
        // --------------------------------------------------------------------------

        model.addAttribute("evaluationTypes", new String[]{"Group", "Individual"});
        model.addAttribute("assessmentTypesOptions", new String[]{"Individual Assessment", "Group Assessment"});

        model.addAttribute("criteria", criteria);
        model.addAttribute("pageTitle", "Edit Criteria Rubric");
        model.addAttribute("formAction", "/criteria/save");
        model.addAttribute("targetFormUrl", "/rubrics/add/" + assessmentId);
        return "criteria-form";
    }

    @PostMapping("/save")
    @Transactional 
    public String saveOrUpdateCriteria(@ModelAttribute Criteria criteria,
                                       @RequestParam(value = "assessmentTypes", required = false) String[] assessmentTypes,
                                       // --- NEW PARAMETER for confirmation flag ---
                                       @RequestParam(value = "confirmedDuplicate", defaultValue = "false") boolean confirmedDuplicate, 
                                       // --- NEW PARAMETER for RedirectAttributes ---
                                       RedirectAttributes redirectAttributes) {
        
        Long assessmentId = criteria.getAssessment().getId();

        // 1. DUPLICATE CHECK (only if not confirmed)
        if (!confirmedDuplicate) {
            boolean isDuplicate = criteriaService.isCriteriaNameDuplicate(
                criteria.getName(), 
                assessmentId, 
                criteria.getId() // pass ID for edit case (null for add case)
            );

            if (isDuplicate) {
                // Flash the name and ID back so we can correctly redirect to the right form (add/edit)
                // We use redirectAttributes to add the 'alert' flag to the URL
                redirectAttributes.addFlashAttribute("criteria", criteria); 
                redirectAttributes.addFlashAttribute("errorMessage", "A Criteria with the name '" + criteria.getName() + "' already exists for this Assessment.");
                redirectAttributes.addAttribute("duplicate", "true");
                
                // Redirect back to the edit/add form
                String redirectUrl = criteria.getId() != null ? 
                                     "redirect:/criteria/edit/" + criteria.getId() : 
                                     "redirect:/criteria/add/" + assessmentId;
                                     
                return redirectUrl;
            }
        }
        
        // 2. NORMAL SAVE LOGIC (Executed if not a duplicate, or if confirmedDuplicate is true)

        if (criteria.getMarks() != null) {
            criteria.setCloMarks(criteria.getMarks().doubleValue());
        } else {
            criteria.setCloMarks(null);
        }
        
        // ... (existing assessmentTypes logic)
        if (assessmentTypes != null) {
            String cleanAssessmentTypes = Arrays.stream(assessmentTypes)
                                                    .distinct()
                                                    .collect(Collectors.joining(", "));
            criteria.setAssessmentTypes(cleanAssessmentTypes);
        } else {
            criteria.setAssessmentTypes(null);
        }
        
        // ... (existing CriteriaRatings logic)
        if (criteria.getCriteriaRatings() != null) {
            for (CriteriaRating rating : criteria.getCriteriaRatings()) {
                rating.setCriteria(criteria);
                if (rating.getLevel() != null) {
                    rating.setRatingMark((double) rating.getLevel());
                }
            }
        }
        
        criteriaService.saveCriteria(criteria);
        return "redirect:/rubrics/view/" + assessmentId;
    }
    
    @PostMapping("/delete/{criteriaId}")
    public String deleteCriteria(@PathVariable Long criteriaId, RedirectAttributes redirectAttributes) {
        Long assessmentId = criteriaService.findCriteriaById(criteriaId).getAssessment().getId();
        try {
            criteriaService.deleteCriteria(criteriaId);
            redirectAttributes.addFlashAttribute("successMessage", "Criteria Rubric deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting criteria rubric.");
        }
        return "redirect:/rubrics/view/" + assessmentId;
    }
}