package com.capstone.adproject.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Deadline;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.DeadlineService;

@Controller
@RequestMapping("/lecturer")
public class LecturerController {
    
    private final AssessmentService assessmentService; 
    private final DeadlineService deadlineService;

    @Autowired
    public LecturerController(AssessmentService assessmentService, DeadlineService deadlineService) {
        this.assessmentService = assessmentService;
        this.deadlineService = deadlineService;
    }
    
    @GetMapping("/home")
    public String lecturerHome(Model model) {
        
        // --- DEFINE UTILITY FUNCTIONS ---
        Function<Object, Boolean> isRubricType = component -> component instanceof Rubric;

        // Simplified grouping - only Rubric components grouped by Assessment Type
        // Uses dummy outer map key to maintain template compatibility
        Function<Assessment, Map<String, Map<String, List<Object>>>> groupAssessmentComponents = assessment -> {
             
            Map<String, Map<String, List<Object>>> finalGroup = new LinkedHashMap<>();
            final String DUMMY_KEY = "ASSESSMENT_GROUPING"; 
            
            // 1. Get all Rubric components
            Stream<Object> combinedComponents = Stream.empty();
            if (assessment.getRubrics() != null) {
                combinedComponents = assessment.getRubrics().stream().map(r -> (Object)r);
            }
            
            List<Object> components = combinedComponents.collect(Collectors.toList());

            // 2. Group components by Assessment Type (inner map)
            Map<String, List<Object>> byAssessType = components.stream()
                .collect(Collectors.groupingBy(c -> {
                    if (c instanceof Rubric) {
                        return ((Rubric) c).getAssessmentTypes(); 
                    }
                    return "Unknown";
                }, LinkedHashMap::new, Collectors.toList()));

            // 3. Wrap in an outer map with a dummy key
            finalGroup.put(DUMMY_KEY, byAssessType);

            return finalGroup;
        };

        // --- FETCH DATA ---
        List<Assessment> allAssessments = assessmentService.findAllAssessmentsWithRubrics();
        model.addAttribute("allAssessments", allAssessments);
        
        // ✅ CHANGED: Use "allDeadlines" instead of "deadlines" to match template
        List<Deadline> allDeadlines = deadlineService.getAllDeadlines();
        model.addAttribute("allDeadlines", allDeadlines);

        // --- EXPOSE UTILITY FUNCTIONS ---
        model.addAttribute("groupAssessmentComponents", groupAssessmentComponents);
        model.addAttribute("isRubricType", isRubricType);
        
        return "lecturer_home";
    }
}