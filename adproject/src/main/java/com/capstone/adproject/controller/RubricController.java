package com.capstone.adproject.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List; // Import List
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
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
import com.capstone.adproject.model.Rating;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.SubRubric;
import com.capstone.adproject.service.RubricService;

@Controller
@RequestMapping("/rubrics")
public class RubricController {

    private final RubricService rubricService;

    public RubricController(RubricService rubricService) {
        this.rubricService = rubricService;
    }
    
    // --------------------------------------------------------------------------------
    // NEW INNER CLASS: Wrapper to prevent SpelEvaluationException in Thymeleaf
    // --------------------------------------------------------------------------------
    public static class RubricCriteriaWrapper {
        private final Object item;

        public RubricCriteriaWrapper(Object item) {
            this.item = item;
        }

        public boolean isRubric() {
            return item instanceof Rubric;
        }

        public boolean isCriteria() {
            return item instanceof Criteria;
        }

        public Object getItem() {
            return item;
        }
        
        public String getName() {
            if (isRubric()) return ((Rubric) item).getName();
            if (isCriteria()) return ((Criteria) item).getName();
            return null;
        }
        
        public String getEvaluationType() {
             if (isRubric()) return ((Rubric) item).getEvaluationType();
             if (isCriteria()) return ((Criteria) item).getEvaluationType();
             return null;
        }
        
        public String getAssessmentTypes() {
             if (isRubric()) return ((Rubric) item).getAssessmentTypes();
             if (isCriteria()) return ((Criteria) item).getAssessmentTypes();
             return null;
        }
    }
    // --------------------------------------------------------------------------------

    // Page to manage assessments (main entry point)
    @GetMapping("/manage")
    public String manageAssessments(Model model) {
        model.addAttribute("assessments", rubricService.findAllAssessments());
        model.addAttribute("newAssessment", new Assessment());
        return "manage-assessments";
    }

    // Save or Update an assessment
    @PostMapping("/assessment/save")
    public String saveAssessment(@ModelAttribute Assessment assessment) {
        if (assessment.getId() != null) {
            Assessment existingAssessment = rubricService.findAssessmentById(assessment.getId());
            existingAssessment.setTitle(assessment.getTitle());
            rubricService.saveAssessment(existingAssessment);
        } else {
            rubricService.saveAssessment(assessment);
        }
        return "redirect:/rubrics/manage";
    }

    // Delete an assessment
    @PostMapping("/assessment/delete/{id}")
    public String deleteAssessment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            rubricService.deleteAssessment(id);
            redirectAttributes.addFlashAttribute("successMessage", "Assessment deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting assessment.");
        }
        return "redirect:/rubrics/manage";
    }

    @GetMapping("/view/{assessmentId}")
    @Transactional
    public String viewAssessmentRubrics(@PathVariable Long assessmentId, Model model) {
        Assessment assessment = rubricService.findAssessmentById(assessmentId); 

        List<RubricCriteriaWrapper> allRubricsAndCriteriaWrappers = new ArrayList<>();
    
        // ********** CRITERIA INITIALIZATION **********
        if (assessment.getCriteria() != null) {
            Hibernate.initialize(assessment.getCriteria()); 
            for (Criteria criteria : assessment.getCriteria()) {
                if (criteria.getCriteriaRatings() != null) {
                    Hibernate.initialize(criteria.getCriteriaRatings());
                } else {
                    criteria.setCriteriaRatings(new ArrayList<>()); // Criteria model uses List
                }
                allRubricsAndCriteriaWrappers.add(new RubricCriteriaWrapper(criteria)); 
            }
        }

        // ********** RUBRIC INITIALIZATION **********
        if (assessment.getRubrics() != null) {
            Hibernate.initialize(assessment.getRubrics());
            for (Rubric rubric : assessment.getRubrics()) {
                if (rubric.getSubRubrics() != null) {
                    Hibernate.initialize(rubric.getSubRubrics()); 
                    
                    rubric.getSubRubrics().forEach(subRubric -> {
                        if (subRubric.getRatings() != null) {
                            Hibernate.initialize(subRubric.getRatings());
                        } else {
                            subRubric.setRatings(new ArrayList<>()); 
                        }
                    });
                } else {
                    // FIX: Rubric model now uses List, initialize with ArrayList
                    rubric.setSubRubrics(new ArrayList<>()); 
                }
                allRubricsAndCriteriaWrappers.add(new RubricCriteriaWrapper(rubric));
            }
        }
        // ********** END INITIALIZATION **********

        // --- GROUPING LOGIC ---
        Map<String, Map<String, List<RubricCriteriaWrapper>>> finalGroupedRubrics = new HashMap<>();
        
        Set<String> allAssessmentTypes = Set.of("Individual Assessment", "Group Assessment");

        for (RubricCriteriaWrapper wrapper : allRubricsAndCriteriaWrappers) {
            String evaluationType = wrapper.getEvaluationType();
            String assessmentTypesString = wrapper.getAssessmentTypes();

            if (evaluationType == null || evaluationType.trim().isEmpty() || 
                assessmentTypesString == null || assessmentTypesString.trim().isEmpty()) {
                continue;
            }

            Map<String, List<RubricCriteriaWrapper>> assessmentTypeMap = finalGroupedRubrics
                .computeIfAbsent(evaluationType, k -> new HashMap<>());
            
            List<String> types = Arrays.stream(assessmentTypesString.split(","))
                                         .map(String::trim)
                                         .filter(s -> !s.isEmpty())
                                         .collect(Collectors.toList());
            
            for (String type : types) {
                if (allAssessmentTypes.contains(type)) {
                    assessmentTypeMap
                        .computeIfAbsent(type, k -> new ArrayList<>())
                        .add(wrapper);
                }
            }
        }
        // --- END GROUPING LOGIC ---


        model.addAttribute("assessment", assessment);
        model.addAttribute("groupedRubrics", finalGroupedRubrics); 
        
        return "view-assessment-rubrics";
    }

    // Show form to ADD a new Likert rubric
    @GetMapping("/add/{assessmentId}")
    public String showAddRubricForm(@PathVariable Long assessmentId, Model model) {
        Assessment assessment = rubricService.findAssessmentById(assessmentId);
        Rubric rubric = new Rubric();
        rubric.setAssessment(assessment);
        
        SubRubric initialSubRubric = new SubRubric();
        initialSubRubric.setRubric(rubric);
        
        List<Rating> ratings = new ArrayList<>();
        for (int i = 0; i <= 4; i++) {
            Rating rating = new Rating();
            rating.setLevel(i);
            rating.setSubRubric(initialSubRubric);
            ratings.add(rating);
        }
        initialSubRubric.setRatings(ratings);
        
        // FIX: Use List instead of Set
        List<SubRubric> subRubrics = new ArrayList<>(); 
        subRubrics.add(initialSubRubric);
        rubric.setSubRubrics(subRubrics);
        
        model.addAttribute("rubric", rubric);
        model.addAttribute("pageTitle", "Add New Likert Rubric"); 
        model.addAttribute("formAction", "/rubrics/save");
        model.addAttribute("targetFormUrl", "/criteria/add/" + assessmentId); 
        
        model.addAttribute("evaluationTypes", new String[]{"Group", "Individual"});
        model.addAttribute("assessmentTypesOptions", new String[]{"Individual Assessment", "Group Assessment"});
        
        return "rubric-form"; 
    }

    // Show form to EDIT an existing Likert rubric
    @GetMapping("/edit/{rubricId}")
    @Transactional
    public String showEditRubricForm(@PathVariable Long rubricId, Model model){
        Rubric rubric = rubricService.findRubricById(rubricId);
        Long assessmentId = rubric.getAssessment().getId(); 
        
        // Ensure subRubrics is initialized as a List
        if (rubric.getSubRubrics() == null) {
            rubric.setSubRubrics(new ArrayList<>());
        }

        // Initialize sub-rubrics and ratings for edit mode
        if (!rubric.getSubRubrics().isEmpty()) { // Check if the List is not empty
            // To prevent ConcurrentModificationException when initializing ratings within the loop,
            // we operate directly on the List returned by the getter.
            for (SubRubric subRubric : rubric.getSubRubrics()) { 
                // Ensure ratings are initialized
                if (subRubric.getRatings() == null || subRubric.getRatings().isEmpty()) {
                    List<Rating> ratings = new ArrayList<>();
                    for (int i = 0; i <= 4; i++) {
                        Rating rating = new Rating();
                        rating.setLevel(i);
                        rating.setSubRubric(subRubric);
                        ratings.add(rating);
                    }
                    subRubric.setRatings(ratings);
                } else {
                    // Sort ratings by level to ensure correct order for indexed form binding
                    List<Rating> sortedRatings = new ArrayList<>(subRubric.getRatings());
                    sortedRatings.sort((r1, r2) -> Integer.compare(r1.getLevel(), r2.getLevel()));
                    subRubric.setRatings(sortedRatings);
                }
            }
        } else {
            // If no sub-rubrics exist, initialize with one in a List
            SubRubric initialSubRubric = new SubRubric();
            initialSubRubric.setRubric(rubric);
            
            List<Rating> ratings = new ArrayList<>();
            for (int i = 0; i <= 4; i++) {
                Rating rating = new Rating();
                rating.setLevel(i);
                rating.setSubRubric(initialSubRubric);
                ratings.add(rating);
            }
            initialSubRubric.setRatings(ratings);
            
            // FIX: Use List for initialization
            List<SubRubric> subRubrics = new ArrayList<>();
            subRubrics.add(initialSubRubric);
            rubric.setSubRubrics(subRubrics);
        }
        
        model.addAttribute("rubric", rubric);
        model.addAttribute("pageTitle", "Edit Likert Rubric");
        model.addAttribute("formAction", "/rubrics/save"); 
        model.addAttribute("targetFormUrl", "/criteria/add/" + assessmentId); 
        
        model.addAttribute("evaluationTypes", new String[]{"Group", "Individual"});
        model.addAttribute("assessmentTypesOptions", new String[]{"Individual Assessment", "Group Assessment"});
        
        return "rubric-form";
    }

    // Save a new Likert rubric or update an existing one
    @PostMapping("/save")
    @Transactional
    public String saveOrUpdateRubric(@ModelAttribute Rubric rubric, 
                                     @RequestParam(value = "assessmentTypes", required = false) String[] assessmentTypes) {
        
        if (rubric.getMarks() != null) {
            rubric.setCloMarks(rubric.getMarks().doubleValue());
        } else {
            rubric.setCloMarks(null);
        }
        
        if (assessmentTypes != null) {
            String cleanAssessmentTypes = Arrays.stream(assessmentTypes)
                                                 .distinct()
                                                 .collect(Collectors.joining(", "));
            rubric.setAssessmentTypes(cleanAssessmentTypes);
        } else {
            rubric.setAssessmentTypes(null);
        }
        
        // Ensure all sub-rubrics and ratings have proper relationships
        if (rubric.getSubRubrics() != null) {
            // SubRubrics is already a List due to model change, no need to convert/cast
            for (SubRubric subRubric : rubric.getSubRubrics()) {
                subRubric.setRubric(rubric);
                
                if (subRubric.getRatings() != null) {
                    List<Rating> ratings = subRubric.getRatings();
                    
                    for (int i = 0; i < ratings.size(); i++) {
                        Rating rating = ratings.get(i);
                        rating.setSubRubric(subRubric);
                        
                        if (rating.getLevel() == null) {
                            rating.setLevel(i);
                        }
                    }
                }
            }
        }
        
        rubricService.saveRubric(rubric);
        
        return "redirect:/rubrics/view/" + rubric.getAssessment().getId();
    }
    
    // Delete a Likert rubric
    @PostMapping("/delete/{rubricId}")
    public String deleteRubric(@PathVariable Long rubricId, RedirectAttributes redirectAttributes) {
        Long assessmentId = rubricService.findRubricById(rubricId).getAssessment().getId();
        try {
            rubricService.deleteRubric(rubricId);
            redirectAttributes.addFlashAttribute("successMessage", "Likert Rubric deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting Likert rubric.");
        }
        return "redirect:/rubrics/view/" + assessmentId;
    }

    // New method to handle the edit assessment form
    @GetMapping("/assessment/edit/{id}")
    public String showEditAssessmentForm(@PathVariable Long id, Model model) {
        Assessment assessment = rubricService.findAssessmentById(id);
        model.addAttribute("newAssessment", assessment);
        model.addAttribute("assessments", rubricService.findAllAssessments());
        return "manage-assessments";
    }
}