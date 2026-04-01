package com.capstone.adproject.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Deadline;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.DeadlineService;

@Controller
@RequestMapping("/lecturer")
public class LecturerController {
    
    private final AssessmentService assessmentService; 
    private final DeadlineService deadlineService;
    private final LecturerRepository lecturerRepository;

    @Autowired
    public LecturerController(AssessmentService assessmentService, DeadlineService deadlineService,
            LecturerRepository lecturerRepository) {
        this.assessmentService = assessmentService;
        this.deadlineService = deadlineService;
        this.lecturerRepository = lecturerRepository;
    }
    
    @GetMapping("/home")
    public String lecturerHome(Model model, Authentication authentication) {
        
        Function<Object, Boolean> isRubricType = component -> component instanceof Rubric;

        Function<Assessment, Map<String, Map<String, List<Object>>>> groupAssessmentComponents = assessment -> {
             
            Map<String, Map<String, List<Object>>> finalGroup = new LinkedHashMap<>();
            final String DUMMY_KEY = "ASSESSMENT_GROUPING"; 
            
            Stream<Object> combinedComponents = Stream.empty();
            if (assessment.getRubrics() != null) {
                combinedComponents = assessment.getRubrics().stream().map(r -> (Object)r);
            }
            
            List<Object> components = combinedComponents.collect(Collectors.toList());

            Map<String, List<Object>> byAssessType = components.stream()
                .collect(Collectors.groupingBy(c -> {
                    if (c instanceof Rubric) {
                        return ((Rubric) c).getAssessmentTypes(); 
                    }
                    return "Unknown";
                }, LinkedHashMap::new, Collectors.toList()));

            finalGroup.put(DUMMY_KEY, byAssessType);

            return finalGroup;
        };

        Long courseId = lecturerRepository.findByEmail(authentication.getName())
            .or(() -> lecturerRepository.findByUsername(authentication.getName()))
            .map(Lecturer::getCourse)
            .map(c -> c != null ? c.getId() : null)
            .orElse(null);

        List<Assessment> allAssessments = assessmentService.findAllAssessmentsWithRubrics().stream()
            .filter(a -> courseId != null && a.getCourse() != null && courseId.equals(a.getCourse().getId()))
            .collect(Collectors.toList());
        model.addAttribute("allAssessments", allAssessments);
        
        List<Deadline> allDeadlines = deadlineService.getAllDeadlines().stream()
            .filter(d -> allAssessments.stream().anyMatch(a -> a.getId().equals(d.getAssessmentId())))
            .collect(Collectors.toList());
        model.addAttribute("allDeadlines", allDeadlines);

        model.addAttribute("groupAssessmentComponents", groupAssessmentComponents);
        model.addAttribute("isRubricType", isRubricType);
        
        return "lecturer_home";
    }
}