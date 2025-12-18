package com.capstone.adproject.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.capstone.adproject.model.Rating;
import com.capstone.adproject.model.Rubric;
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

        // ✅ Initialize display orders for existing rubrics if they don't have them
        rubricService.initializeRubricOrders(assessmentId);
        
        // Reload to get updated display orders
        assessment = rubricService.findAssessmentById(assessmentId);

        Map<String, Map<String, List<Object>>> groupedRubrics = new LinkedHashMap<>();
        final String DUMMY_KEY = "ASSESSMENT_GROUPING";
        Map<String, List<Object>> innerGroup = new LinkedHashMap<>();
    
        if (assessment.getRubrics() != null) {
            Hibernate.initialize(assessment.getRubrics());
            for (Rubric rubric : assessment.getRubrics()) {
                if (rubric.getSubRubrics() != null) {
                    Hibernate.initialize(rubric.getSubRubrics()); 
                    rubric.getSubRubrics().forEach(subRubric -> {
                        if (subRubric.getRatings() != null) {
                            Hibernate.initialize(subRubric.getRatings());
                        }
                    });
                }
                if (rubric.getRatings() != null) {
                    Hibernate.initialize(rubric.getRatings());
                }

                String assessmentType = rubric.getAssessmentTypes();
                if (assessmentType == null || assessmentType.trim().isEmpty()) {
                    assessmentType = "Uncategorized";
                }
                innerGroup.computeIfAbsent(assessmentType, k -> new ArrayList<>()).add(rubric);
            }
        }
        
        // Sort rubrics within each assessment type by display_order
        for (List<Object> rubrics : innerGroup.values()) {
            rubrics.sort((o1, o2) -> {
                if (o1 instanceof Rubric && o2 instanceof Rubric) {
                    Integer order1 = ((Rubric) o1).getDisplayOrder();
                    Integer order2 = ((Rubric) o2).getDisplayOrder();
                    if (order1 == null) order1 = 0;
                    if (order2 == null) order2 = 0;
                    return Integer.compare(order1, order2);
                }
                return 0;
            });
        }
        
        groupedRubrics.put(DUMMY_KEY, innerGroup);

        model.addAttribute("assessment", assessment);
        model.addAttribute("groupedRubrics", groupedRubrics); 
        
        return "view-assessment-rubrics";
    }

    @GetMapping("/add/{assessmentId}")
    public String showAddRubricForm(@PathVariable Long assessmentId, Model model) {
        Assessment assessment = rubricService.findAssessmentById(assessmentId);
        Rubric rubric = new Rubric();
        rubric.setAssessment(assessment);
        
        rubric.setSubRubrics(new ArrayList<>());
        rubric.setRatings(new ArrayList<>());
        
        model.addAttribute("rubric", rubric);
        model.addAttribute("pageTitle", "Add New Rubric"); 
        model.addAttribute("formAction", "/rubrics/save");
        
        model.addAttribute("assessmentTypesOptions", new String[]{"Individual Assessment", "Group Assessment"});
        
        return "rubric-form"; 
    }

    @GetMapping("/edit/{rubricId}")
    @Transactional
    public String showEditRubricForm(@PathVariable Long rubricId, Model model) {
        if (!model.containsAttribute("rubric")) {
            Rubric rubric = rubricService.findRubricById(rubricId);
            
            if (rubric.getSubRubrics() == null) {
                rubric.setSubRubrics(new ArrayList<>());
            } else {
                for (SubRubric subRubric : rubric.getSubRubrics()) {
                    if (subRubric.getRatings() == null) {
                        subRubric.setRatings(new ArrayList<>());
                    }
                }
            }
            
            if (rubric.getRatings() == null) {
                rubric.setRatings(new ArrayList<>());
            }
            
            model.addAttribute("rubric", rubric);
        }
        
        model.addAttribute("pageTitle", "Edit Rubric");
        model.addAttribute("formAction", "/rubrics/save"); 
        
        model.addAttribute("assessmentTypesOptions", new String[]{"Individual Assessment", "Group Assessment"});
        
        return "rubric-form";
    }

    @PostMapping("/save")
    @Transactional
    public String saveOrUpdateRubric(@ModelAttribute Rubric rubric, 
                                     @RequestParam(value = "duplicateConfirmed", defaultValue = "false") boolean duplicateConfirmed,
                                     @RequestParam Map<String, String> allParams,
                                     RedirectAttributes redirectAttributes,
                                     Model model) {
        
        Long assessmentId = rubric.getAssessment().getId();
        Long rubricId = rubric.getId();

        // ✅ CRITICAL FIX: Manually process checkbox values from raw parameters
        List<String> commentLabels = rubric.getRubricCommentLabels();
        if (commentLabels != null && !commentLabels.isEmpty()) {
            List<Boolean> anonymousFlags = new ArrayList<>();
            
            for (int i = 0; i < commentLabels.size(); i++) {
                String paramKey = "rubricCommentAnonymousFlags[" + i + "]";
                boolean isChecked = allParams.containsKey(paramKey) && "true".equals(allParams.get(paramKey));
                anonymousFlags.add(isChecked);
            }
            
            rubric.setRubricCommentAnonymousFlags(anonymousFlags);
        }

        // Duplicate check
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

        // ✅ REMOVED: validateIndividualRatingMarks() call - no more marks validation

        // Set CLO Marks equal to Total Marks
        if (rubric.getMarks() != null) {
            rubric.setCloMarks(rubric.getMarks().doubleValue());
        } else {
            rubric.setCloMarks(null);
        }
        
        // Set bidirectional relationships for sub-rubrics
        if (rubric.getSubRubrics() != null) {
            for (SubRubric subRubric : rubric.getSubRubrics()) {
                subRubric.setRubric(rubric);
                
                if (subRubric.getRatings() != null) {
                    for (Rating rating : subRubric.getRatings()) {
                        rating.setSubRubric(subRubric);
                        rating.setRubric(null);
                    }
                }
            }
        }
        
        // Set bidirectional relationships for direct ratings
        if (rubric.getRatings() != null) {
            for (Rating rating : rubric.getRatings()) {
                rating.setRubric(rubric);
                rating.setSubRubric(null);
            }
        }
        
        Rubric savedRubric = rubricService.saveRubric(rubric);

        redirectAttributes.addFlashAttribute("successMessage", "Rubric saved successfully.");
        
        return "redirect:/rubrics/view/" + savedRubric.getAssessment().getId();
    }

    // ✅ REMOVED: validateIndividualRatingMarks() method entirely
    
    @PostMapping("/delete/{rubricId}")
    public String deleteRubric(@PathVariable Long rubricId, RedirectAttributes redirectAttributes) {
        Long assessmentId = rubricService.findRubricById(rubricId).getAssessment().getId();
        try {
            rubricService.deleteRubric(rubricId);
            redirectAttributes.addFlashAttribute("successMessage", "Rubric deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting rubric.");
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
    
    // ==================== REORDERING ENDPOINTS ====================
    
    /**
     * Move entire assessment type block (Individual/Group) up or down
     */
    @PostMapping("/assessment/{assessmentId}/move-block")
    public String moveAssessmentBlock(@PathVariable Long assessmentId,
                                       @RequestParam String blockType,
                                       @RequestParam String direction,
                                       RedirectAttributes redirectAttributes) {
        try {
            rubricService.moveAssessmentBlock(assessmentId, blockType, direction);
        } catch (Exception e) {
            // Silent fail
        }
        return "redirect:/rubrics/view/" + assessmentId;
    }
    
    /**
     * Move individual rubric up or down within its assessment type
     */
    @PostMapping("/rubric/{rubricId}/move")
    public String moveRubric(@PathVariable Long rubricId,
                              @RequestParam String direction,
                              RedirectAttributes redirectAttributes) {
        try {
            Rubric rubric = rubricService.findRubricById(rubricId);
            Long assessmentId = rubric.getAssessment().getId();
            
            rubricService.moveRubric(rubricId, direction);
            
            return "redirect:/rubrics/view/" + assessmentId;
        } catch (Exception e) {
            return "redirect:/rubrics/manage";
        }
    }
}