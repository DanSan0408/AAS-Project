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
import com.capstone.adproject.model.Criteria;
import com.capstone.adproject.model.Deadline;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.DeadlineService;

@Controller
@RequestMapping("/supervisor")
public class IndustrialSupervisorController {
    
    private final AssessmentService assessmentService; 
    private final DeadlineService deadlineService;

    @Autowired
    public IndustrialSupervisorController(AssessmentService assessmentService, DeadlineService deadlineService) {
        this.assessmentService = assessmentService;
        this.deadlineService = deadlineService;
    }
    
    @GetMapping("/home")
    public String industrialSupervisorHome(Model model) {
        
        // --- DEFINE UTILITY FUNCTIONS (Same as StudentController) ---
        Function<Object, Boolean> isRubricType = component -> component instanceof Rubric;

        Function<Assessment, Map<String, Map<String, List<Object>>>> groupAssessmentComponents = assessment -> {
            
            Stream<Object> combinedComponents = Stream.concat(
                assessment.getRubrics().stream().map(r -> (Object)r),
                assessment.getCriteria().stream().map(c -> (Object)c)
            );
            
            List<Object> components = combinedComponents.collect(Collectors.toList());

            Map<String, List<Object>> byEvalType = components.stream()
                .collect(Collectors.groupingBy(c -> {
                    if (c instanceof Rubric) {
                        return ((Rubric) c).getEvaluationType();
                    } else if (c instanceof Criteria) {
                        return ((Criteria) c).getEvaluationType();
                    }
                    return "Unknown";
                }, LinkedHashMap::new, Collectors.toList()));

            Map<String, Map<String, List<Object>>> finalGroup = new LinkedHashMap<>();
            
            byEvalType.forEach((evalType, evalComponents) -> {
                Map<String, List<Object>> byAssessType = evalComponents.stream()
                    .collect(Collectors.groupingBy(c -> {
                        if (c instanceof Rubric) {
                            return ((Rubric) c).getAssessmentTypes();
                        } else if (c instanceof Criteria) {
                            return ((Criteria) c).getAssessmentTypes();
                        }
                        return "Unknown";
                    }, LinkedHashMap::new, Collectors.toList()));
                
                finalGroup.put(evalType, byAssessType);
            });

            return finalGroup;
        };

        // --- FETCH DATA ---
        List<Assessment> allAssessments = assessmentService.findAllAssessmentsWithRubrics();
        model.addAttribute("allAssessments", allAssessments);
        
        List<Deadline> deadlines = deadlineService.getAllDeadlines();
        model.addAttribute("deadlines", deadlines);

        // --- EXPOSE UTILITY FUNCTIONS ---
        model.addAttribute("groupAssessmentComponents", groupAssessmentComponents);
        model.addAttribute("isRubricType", isRubricType);
        
        return "industrial_supervisor_home";
    }
}