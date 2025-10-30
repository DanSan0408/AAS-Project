package com.capstone.adproject.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
import com.capstone.adproject.model.RubricCriteriaWrapper;
import com.capstone.adproject.model.SubRubric;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.RubricService;

@Controller
@RequestMapping("/rubrics")
public class RubricController {

    private final RubricService rubricService;
    private final AssessmentService assessmentService;

    public RubricController(RubricService rubricService, AssessmentService assessmentService) {
        this.rubricService = rubricService;
        this.assessmentService = assessmentService;
    }

    @GetMapping("/manage")
    public String manageAssessments(Model model) {
        model.addAttribute("assessments", rubricService.findAllAssessments());
        
        if (!model.containsAttribute("newAssessment")) {
            model.addAttribute("newAssessment", new Assessment());
        }
        
        return "manage-assessments";
    }

    @PostMapping("/assessment/save")
    public String saveAssessment(@ModelAttribute Assessment assessment,
                                 @RequestParam(value = "duplicateConfirmed", defaultValue = "false") boolean duplicateConfirmed,
                                 RedirectAttributes redirectAttributes) {

        Long assessmentId = assessment.getId();

        if (!duplicateConfirmed) {
            boolean isDuplicate = assessmentService.isAssessmentTitleDuplicate(
                assessment.getTitle(),
                assessmentId
            );

            if (isDuplicate) {
                redirectAttributes.addFlashAttribute("newAssessment", assessment); 
                redirectAttributes.addFlashAttribute("duplicateFound", true);
                redirectAttributes.addFlashAttribute("errorMessage",
                    "An Assessment with the title '" + assessment.getTitle() + "' already exists. Click 'Save' again to confirm the duplicate entry."
                );
                return "redirect:/rubrics/manage";
            }
        }

        if (assessment.getId() != null) {
            Assessment existingAssessment = rubricService.findAssessmentById(assessment.getId());
            existingAssessment.setTitle(assessment.getTitle());
            rubricService.saveAssessment(existingAssessment);
            redirectAttributes.addFlashAttribute("successMessage", "Assessment updated successfully.");
        } else {
            rubricService.saveAssessment(assessment);
            redirectAttributes.addFlashAttribute("successMessage", "New Assessment created successfully.");
        }

        return "redirect:/rubrics/manage";
    }

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
    
        if (assessment.getCriteria() != null) {
            Hibernate.initialize(assessment.getCriteria()); 
            for (Criteria criteria : assessment.getCriteria()) {
                if (criteria.getCriteriaRatings() != null) {
                    Hibernate.initialize(criteria.getCriteriaRatings());
                } else {
                    criteria.setCriteriaRatings(new ArrayList<>());
                }
                allRubricsAndCriteriaWrappers.add(new RubricCriteriaWrapper(criteria)); 
            }
        }

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
                    rubric.setSubRubrics(new ArrayList<>()); 
                }
                allRubricsAndCriteriaWrappers.add(new RubricCriteriaWrapper(rubric));
            }
        }

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

        model.addAttribute("assessment", assessment);
        model.addAttribute("groupedRubrics", finalGroupedRubrics); 
        
        return "view-assessment-rubrics";
    }

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

    @GetMapping("/edit/{rubricId}")
    @Transactional
    public String showEditRubricForm(@PathVariable Long rubricId, Model model) {
        // CRITICAL FIX: Check if rubric data exists in flash attributes (from duplicate check redirect)
        if (!model.containsAttribute("rubric")) {
            Rubric rubric = rubricService.findRubricById(rubricId);
            
            if (rubric.getSubRubrics() == null) {
                rubric.setSubRubrics(new ArrayList<>());
            }

            if (!rubric.getSubRubrics().isEmpty()) {
                for (SubRubric subRubric : rubric.getSubRubrics()) {
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
                        List<Rating> sortedRatings = new ArrayList<>(subRubric.getRatings());
                        sortedRatings.sort((r1, r2) -> Integer.compare(r1.getLevel(), r2.getLevel()));
                        subRubric.getRatings().clear();
for (Rating rating : sortedRatings) {
    rating.setSubRubric(subRubric);
    subRubric.getRatings().add(rating);
}

                    }
                }
            } else {
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
                
                List<SubRubric> subRubrics = new ArrayList<>();
                subRubrics.add(initialSubRubric);
                rubric.setSubRubrics(subRubrics);
            }
            
            model.addAttribute("rubric", rubric);
        }
        
        Long assessmentId = ((Rubric) model.getAttribute("rubric")).getAssessment().getId();
        
        model.addAttribute("pageTitle", "Edit Likert Rubric");
        model.addAttribute("formAction", "/rubrics/save"); 
        model.addAttribute("targetFormUrl", "/criteria/add/" + assessmentId); 
        
        model.addAttribute("evaluationTypes", new String[]{"Group", "Individual"});
        model.addAttribute("assessmentTypesOptions", new String[]{"Individual Assessment", "Group Assessment"});
        
        return "rubric-form";
    }

    @PostMapping("/save")
    @Transactional
    public String saveOrUpdateRubric(@ModelAttribute Rubric rubric, 
                                     @RequestParam(value = "assessmentTypes", required = false) String[] assessmentTypes,
                                     @RequestParam(value = "duplicateConfirmed", defaultValue = "false") boolean duplicateConfirmed,
                                     RedirectAttributes redirectAttributes,
                                     Model model) {
        
        Long assessmentId = rubric.getAssessment().getId();
        Long rubricId = rubric.getId();

        if (!duplicateConfirmed) {
            boolean isDuplicate = rubricService.isRubricNameDuplicate(
                rubric.getName(), 
                assessmentId, 
                rubricId
            );

            if (isDuplicate) {
                redirectAttributes.addFlashAttribute("rubric", rubric);
                redirectAttributes.addFlashAttribute("duplicateFound", true);
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "A Rubric with the name '" + rubric.getName() + "' already exists in this assessment. Click 'Save' again to confirm the duplicate entry."
                );
                
                String redirectPath = (rubricId == null) ? "/rubrics/add/" + assessmentId : "/rubrics/edit/" + rubricId;
                return "redirect:" + redirectPath;
            }
        }

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
        
        if (rubric.getSubRubrics() != null) {
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
        
        Rubric savedRubric = rubricService.saveRubric(rubric);

        redirectAttributes.addFlashAttribute("successMessage", "Rubric saved successfully.");
        
        return "redirect:/rubrics/view/" + savedRubric.getAssessment().getId();
    }
    
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

    @GetMapping("/assessment/edit/{id}")
    public String showEditAssessmentForm(@PathVariable Long id, Model model) {
        Assessment assessment = rubricService.findAssessmentById(id);
        model.addAttribute("newAssessment", assessment);
        model.addAttribute("assessments", rubricService.findAllAssessments());
        return "manage-assessments";
    }
}