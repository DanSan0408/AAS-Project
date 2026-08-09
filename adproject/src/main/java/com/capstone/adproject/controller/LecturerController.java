package com.capstone.adproject.controller;


import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Deadline;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.LecturerRubricAssignmentRepository;
import com.capstone.adproject.repositories.LecturerStudentAssignmentRepository;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.DeadlineService;

@Controller
@RequestMapping("/lecturer")
public class LecturerController {
    
    private final AssessmentService assessmentService; 
    private final DeadlineService deadlineService;
    private final LecturerRepository lecturerRepository;
    private final LecturerGroupAssignmentRepository assignmentRepository;
    private final LecturerRubricAssignmentRepository rubricAssignmentRepository;
    private final LecturerStudentAssignmentRepository studentAssignmentRepository;

    @Autowired
    public LecturerController(AssessmentService assessmentService, DeadlineService deadlineService,
            LecturerRepository lecturerRepository,
            LecturerGroupAssignmentRepository assignmentRepository,
            LecturerRubricAssignmentRepository rubricAssignmentRepository,
            LecturerStudentAssignmentRepository studentAssignmentRepository) {
        this.assessmentService = assessmentService;
        this.deadlineService = deadlineService;
        this.lecturerRepository = lecturerRepository;
        this.assignmentRepository = assignmentRepository;
        this.rubricAssignmentRepository = rubricAssignmentRepository;
        this.studentAssignmentRepository = studentAssignmentRepository;
    }
    
    @GetMapping("/home")
    public String lecturerHome(Model model, Authentication authentication) {
        
        Lecturer lecturer = lecturerRepository.findByEmail(authentication.getName())
            .or(() -> lecturerRepository.findByUsername(authentication.getName()))
            .orElse(null);
            
        Long courseId = lecturer != null && lecturer.getCourse() != null ? lecturer.getCourse().getId() : null;

        Set<Assessment> combinedAssessments = new HashSet<>();
        Map<Long, String> assessmentLaunchModes = new HashMap<>();
        Map<Long, List<String>> assessmentTargets = new HashMap<>();
        
        if (lecturer != null) {
            List<Assessment> groupAssessments = assignmentRepository.findAssessmentsByLecturer(lecturer);
            List<Assessment> rubricAssessments = rubricAssignmentRepository.findAssessmentsByLecturer(lecturer);
            List<Assessment> studentAssessments = studentAssignmentRepository.findAssessmentsByLecturer(lecturer);
            
            combinedAssessments.addAll(groupAssessments);
            combinedAssessments.addAll(rubricAssessments);
            combinedAssessments.addAll(studentAssessments);
            
            Set<Long> studentAssessmentIds = studentAssessments.stream().map(Assessment::getId).collect(Collectors.toSet());
            Set<Long> rubricAssessmentIds = rubricAssessments.stream().map(Assessment::getId).collect(Collectors.toSet());

            for (Assessment assessment : combinedAssessments) {
                if (studentAssessmentIds.contains(assessment.getId())) {
                    assessmentLaunchModes.put(assessment.getId(), "STUDENT");
                    List<String> targets = studentAssignmentRepository.findByAssessment(assessment).stream()
                        .filter(a -> a.getLecturer() != null && a.getLecturer().getId().equals(lecturer.getId()))
                        .map(a -> a.getStudent() != null ? 
                            (a.getStudent().getUsername() != null && !a.getStudent().getUsername().trim().isEmpty() 
                                ? a.getStudent().getUsername() : a.getStudent().getEmail()) 
                            : "Unknown Student")
                        .collect(Collectors.toList());
                    assessmentTargets.put(assessment.getId(), targets);
                } else if (rubricAssessmentIds.contains(assessment.getId())) {
                    assessmentLaunchModes.put(assessment.getId(), "GROUP");
                    assessmentTargets.put(assessment.getId(), List.of("All Groups (Rubric Mode)"));
                } else {
                    assessmentLaunchModes.put(assessment.getId(), "GROUP");
                    List<String> targets = assignmentRepository.findByAssessment(assessment).stream()
                        .filter(a -> a.getLecturer() != null && a.getLecturer().getId().equals(lecturer.getId()))
                        .map(a -> a.getGroup() != null ? a.getGroup().getGroupName() : "Unknown Group")
                        .collect(Collectors.toList());
                    assessmentTargets.put(assessment.getId(), targets);
                }
            }
        }
        
        LocalDateTime now = LocalDateTime.now();
        List<Assessment> pendingTasks = combinedAssessments.stream().filter(assessment -> {
            List<Deadline> deadlines = deadlineService.getDeadlinesByAssessmentId(assessment.getId()).stream()
                .filter(d -> d.getAssessorType() == null 
                          || "LECTURER".equalsIgnoreCase(d.getAssessorType()) 
                          || "GENERAL".equalsIgnoreCase(d.getAssessorType()) 
                          || "SUPERVISOR".equalsIgnoreCase(d.getAssessorType()))
                .collect(Collectors.toList());
            
            if (deadlines.isEmpty()) {
                return true;
            }
            
            return deadlines.stream().anyMatch(d -> {
                if (d.getDate() == null) return true;
                LocalDateTime endDate = d.getDate().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDateTime();
                return now.isBefore(endDate);
            });
        }).collect(Collectors.toList());

        List<Long> openAssessmentIds = pendingTasks.stream()
            .filter(assessment -> {
                List<Deadline> deadlines = deadlineService.getDeadlinesByAssessmentId(assessment.getId()).stream()
                    .filter(d -> d.getAssessorType() == null 
                              || "LECTURER".equalsIgnoreCase(d.getAssessorType()) 
                              || "GENERAL".equalsIgnoreCase(d.getAssessorType()) 
                              || "SUPERVISOR".equalsIgnoreCase(d.getAssessorType()))
                    .collect(Collectors.toList());
                
                return deadlines.stream().anyMatch(d -> {
                    if (d.getDate() == null) return true;
                    LocalDateTime openDate = d.getOpenDate() != null ? 
                        d.getOpenDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime() : 
                        LocalDateTime.MIN;
                    LocalDateTime endDate = d.getDate().toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalDateTime();
                    return now.isAfter(openDate) && now.isBefore(endDate);
                });
            })
            .map(Assessment::getId)
            .collect(Collectors.toList());

        model.addAttribute("pendingTasks", pendingTasks);
        model.addAttribute("openAssessmentIds", openAssessmentIds);
        model.addAttribute("assessmentLaunchModes", assessmentLaunchModes);
        model.addAttribute("assessmentTargets", assessmentTargets);

        List<Assessment> allAssessments = assessmentService.findAllAssessmentsWithRubrics().stream()
            .filter(a -> courseId != null && a.getCourse() != null && courseId.equals(a.getCourse().getId()))
            .collect(Collectors.toList());
        model.addAttribute("allAssessments", allAssessments);
        
        long nowMillis = System.currentTimeMillis();
        List<Deadline> allDeadlines = courseId == null
            ? List.of()
            : deadlineService.getDeadlinesByCourseId(courseId);
        List<Deadline> filteredDeadlines = allDeadlines.stream()
            .filter(d -> d.getAssessmentId() != null && combinedAssessments.stream().anyMatch(a -> a.getId().equals(d.getAssessmentId())))
            .filter(d -> d.getDate() != null && (d.getDate().getTime() + 86399999L) >= nowMillis)
            .collect(Collectors.toList());
        model.addAttribute("allDeadlines", allDeadlines);
        model.addAttribute("deadlines", filteredDeadlines);
        
        return "lecturer_home";
    }
}