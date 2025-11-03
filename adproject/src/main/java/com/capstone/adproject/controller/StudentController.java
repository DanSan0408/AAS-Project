package com.capstone.adproject.controller;

import java.util.LinkedHashMap;
import java.util.List; // Need Criteria model for type check
import java.util.Map; // Need Rubric model for type check
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
import com.capstone.adproject.model.Rubric; // Crucial import for defining the utilities
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.DeadlineService;

@Controller
@RequestMapping("/student")
public class StudentController {
    
    private final AssessmentService assessmentService; 
    private final DeadlineService deadlineService;

    @Autowired
    public StudentController(AssessmentService assessmentService, DeadlineService deadlineService) {
        this.assessmentService = assessmentService;
        this.deadlineService = deadlineService;
    }
    
    @GetMapping("/home")
    public String studentHome(Model model) {
        
        // --- 1. DEFINE UTILITY FUNCTIONS LOCALLY ---

        // Function 1: Replaces 'isRubricType.apply(component)'
        // This function checks if a component is a Rubric (Likert Scale) or a Criteria-based component.
        // It must handle the incoming object from Thymeleaf's iteration loop.
        Function<Object, Boolean> isRubricType = component -> component instanceof Rubric;

        // Function 2: Replaces 'groupAssessmentComponents.apply(assessment)'
        // This function groups both Rubric and Criteria lists within an Assessment object.
        Function<Assessment, Map<String, Map<String, List<Object>>>> groupAssessmentComponents = assessment -> {
            
            // Combine both lists into a single stream of Objects (since they don't share an interface)
            Stream<Object> combinedComponents = Stream.concat(
                assessment.getRubrics().stream().map(r -> (Object)r),
                assessment.getCriteria().stream().map(c -> (Object)c)
            );
            
            // Collect into a list for grouping
            List<Object> components = combinedComponents.collect(Collectors.toList());

            // 1. Group by Evaluation Type (e.g., "Group" or "Individual")
            // We use a safe accessor here, assuming Rubric/Criteria have getEvaluationType()
            Map<String, List<Object>> byEvalType = components.stream()
                .collect(Collectors.groupingBy(c -> {
                    if (c instanceof Rubric) {
                        return ((Rubric) c).getEvaluationType();
                    } else if (c instanceof Criteria) {
                        return ((Criteria) c).getEvaluationType();
                    }
                    return "Unknown"; // Fallback
                }, LinkedHashMap::new, Collectors.toList()));

            // 2. Group the second level by Assessment Type (e.g., "Peer Assessment")
            Map<String, Map<String, List<Object>>> finalGroup = new LinkedHashMap<>();
            
            byEvalType.forEach((evalType, evalComponents) -> {
                Map<String, List<Object>> byAssessType = evalComponents.stream()
                    .collect(Collectors.groupingBy(c -> {
                        if (c instanceof Rubric) {
                            return ((Rubric) c).getAssessmentTypes();
                        } else if (c instanceof Criteria) {
                            return ((Criteria) c).getAssessmentTypes();
                        }
                        return "Unknown"; // Fallback
                    }, LinkedHashMap::new, Collectors.toList()));
                
                finalGroup.put(evalType, byAssessType);
            });

            return finalGroup;
        };

        // --- 2. FETCH DATA ---
        List<Assessment> allAssessments = assessmentService.findAllAssessmentsWithRubrics();
        model.addAttribute("allAssessments", allAssessments);
        
        List<Deadline> deadlines = deadlineService.getAllDeadlines();
        model.addAttribute("deadlines", deadlines);

        // --- 3. EXPOSE UTILITY FUNCTIONS TO THE MODEL ---
        // This is the crucial step to resolve the template parsing error.
        model.addAttribute("groupAssessmentComponents", groupAssessmentComponents);
        model.addAttribute("isRubricType", isRubricType);
        
        return "student_home";
    }

    
}