package com.capstone.adproject.controller;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Deadline;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.repositories.AssessmentCommentRepository;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.RubricRepository;
import com.capstone.adproject.service.DeadlineService;
import com.capstone.adproject.service.LecturerAssessmentService;
import com.capstone.adproject.service.RubricService;

@Controller
@RequestMapping("/lecturer/assessments")
public class LecturerAssessmentController {

    private static final Logger logger = LoggerFactory.getLogger(LecturerAssessmentController.class);

    private final LecturerAssessmentService lecturerAssessmentService;
    private final RubricService rubricService;
    private final DeadlineService deadlineService;
    private final LecturerRepository lecturerRepository;
    private final LecturerGroupAssignmentRepository assignmentRepository;
    private final RubricRepository rubricRepository;
    private final AssessmentCommentRepository assessmentCommentRepository;

    public LecturerAssessmentController(
            LecturerAssessmentService lecturerAssessmentService,
            RubricService rubricService,
            DeadlineService deadlineService,
            LecturerRepository lecturerRepository,
            LecturerGroupAssignmentRepository assignmentRepository,
            RubricRepository rubricRepository,
            AssessmentCommentRepository assessmentCommentRepository) {
        this.lecturerAssessmentService = lecturerAssessmentService;
        this.rubricService = rubricService;
        this.deadlineService = deadlineService;
        this.lecturerRepository = lecturerRepository;
        this.assignmentRepository = assignmentRepository;
        this.rubricRepository = rubricRepository;
        this.assessmentCommentRepository = assessmentCommentRepository;
    }

    private Lecturer getCurrentLecturer(Authentication authentication) {
    String emailOrUsername = authentication.getName(); 
    
    Optional<Lecturer> lecturer = lecturerRepository.findByEmail(emailOrUsername);
    if (lecturer.isPresent()) {
        return lecturer.get();
    }
    
    lecturer = lecturerRepository.findByUsername(emailOrUsername);
    if (lecturer.isPresent()) {
        return lecturer.get();
    }
    
    throw new RuntimeException("Lecturer not found: " + emailOrUsername);
}

    @GetMapping
    public String showAssessments(Model model, Authentication authentication) {
        Lecturer lecturer = getCurrentLecturer(authentication);
        
        List<Assessment> assignedAssessments = assignmentRepository.findAssessmentsByLecturer(lecturer);
        
        LocalDateTime now = LocalDateTime.now();
        List<Long> openAssessmentIds = assignedAssessments.stream()
            .filter(assessment -> {
                List<Deadline> deadlines = deadlineService.getDeadlinesByAssessmentIdAndAssessorType(
                    assessment.getId(), "LECTURER");
                
                return deadlines.stream().anyMatch(d -> {
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
        
        model.addAttribute("assessments", assignedAssessments);
        model.addAttribute("openAssessmentIds", openAssessmentIds);
        
        return "lecturer_assessments";
    }

    @GetMapping("/{assessmentId}/select-group")
    public String showGroupSelection(
            @PathVariable Long assessmentId,
            Model model,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        Lecturer lecturer = getCurrentLecturer(authentication);
        
        List<Deadline> lecturerDeadlines = deadlineService.getDeadlinesByAssessmentIdAndAssessorType(
            assessmentId, "LECTURER");
        
        if (lecturerDeadlines.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "This assessment has not been opened by the admin yet.");
            return "redirect:/lecturer/assessments";
        }
        
        LocalDateTime now = LocalDateTime.now();
        boolean isOpen = lecturerDeadlines.stream()
            .anyMatch(d -> {
                LocalDateTime openDate = d.getOpenDate() != null ? 
                    d.getOpenDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime() : 
                    LocalDateTime.MIN;
                LocalDateTime endDate = d.getDate().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDateTime();
                return now.isAfter(openDate) && now.isBefore(endDate);
            });
        
        if (!isOpen) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "This assessment is not currently open for evaluation.");
            return "redirect:/lecturer/assessments";
        }

        Assessment assessment = rubricService.findAssessmentById(assessmentId);
        
        List<Group> assignedGroups = assignmentRepository.findGroupsByLecturerAndAssessment(lecturer, assessment);
        
        if (assignedGroups.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "You have no groups assigned for this assessment.");
            return "redirect:/lecturer/assessments";
        }
        
        Map<Long, String> groupEvaluationStatus = new HashMap<>();
        for (Group group : assignedGroups) {
            String status = lecturerAssessmentService.getGroupEvaluationStatus(
                assessment, lecturer, group.getId());
            groupEvaluationStatus.put(group.getId(), status);
        }
        
        model.addAttribute("assessment", assessment);
        model.addAttribute("assignedGroups", assignedGroups);
        model.addAttribute("groupEvaluationStatus", groupEvaluationStatus);

        return "lecturer_select_group";
    }

    @GetMapping("/{assessmentId}/evaluate")
    public String showCombinedEvaluationForm(
            @PathVariable Long assessmentId,
            @RequestParam Long groupId,
            @RequestParam(required = false) Boolean confirmReevaluation,
            Model model,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        Assessment assessment = rubricService.findAssessmentById(assessmentId);
        Lecturer lecturer = getCurrentLecturer(authentication);

        List<Group> assignedGroups = assignmentRepository.findGroupsByLecturerAndAssessment(lecturer, assessment);
        Group selectedGroup = assignedGroups.stream()
            .filter(g -> g.getId().equals(groupId))
            .findFirst()
            .orElse(null);
        
        if (selectedGroup == null) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "You are not assigned to evaluate this group.");
            return "redirect:/lecturer/assessments/" + assessmentId + "/select-group";
        }

        String evaluationStatus = lecturerAssessmentService.getGroupEvaluationStatus(
            assessment, lecturer, groupId);
        
        boolean isComplete = "completed".equals(evaluationStatus);

        if (isComplete && (confirmReevaluation == null || !confirmReevaluation)) {
            model.addAttribute("assessment", assessment);
            model.addAttribute("selectedGroup", selectedGroup);
            model.addAttribute("showReevaluationWarning", true);
            return "lecturer_reevaluation_warning_combined";
        }

        List<Rubric> groupRubrics = assessment.getRubrics().stream()
            .filter(r -> r.getAssessmentTypes() != null && 
                        r.getAssessmentTypes().equalsIgnoreCase("Group Assessment"))
            .collect(Collectors.toList());

        List<Rubric> individualRubrics = assessment.getRubrics().stream()
            .filter(r -> r.getAssessmentTypes() != null && 
                        r.getAssessmentTypes().equalsIgnoreCase("Individual Assessment"))
            .collect(Collectors.toList());

        List<Student> groupStudents = lecturerAssessmentService.getStudentsByGroup(groupId);

        Map<String, Long> existingGroupMarks = new HashMap<>();
        Map<Integer, String> existingGroupComments = new HashMap<>();
        Map<Long, Map<String, Long>> existingIndividualMarks = new HashMap<>();
        Map<Long, Map<Integer, String>> existingIndividualComments = new HashMap<>();
        
        Map<Long, Map<Integer, String>> existingGroupRubricComments = new HashMap<>();
        Map<Long, Map<Long, Map<Integer, String>>> existingIndividualRubricComments = new HashMap<>();
        
        if (!"not_started".equals(evaluationStatus) && !groupStudents.isEmpty()) {
            Student sampleStudent = groupStudents.get(0);
            
            existingGroupMarks = lecturerAssessmentService.getExistingMarks(
                assessment, lecturer, sampleStudent, "Group Assessment");
            existingGroupComments = lecturerAssessmentService.getExistingComments(
                assessment, lecturer, sampleStudent, "Group Assessment");
            
            for (Rubric rubric : groupRubrics) {
                if (rubric.getRubricCommentCount() != null && rubric.getRubricCommentCount() > 0) {
                    Map<Integer, String> rubricComments = lecturerAssessmentService.getExistingRubricComments(
                        assessment, lecturer, sampleStudent, rubric.getId());
                    existingGroupRubricComments.put(rubric.getId(), rubricComments);
                }
            }
            
            for (Student student : groupStudents) {
                Map<String, Long> studentMarks = lecturerAssessmentService.getExistingMarks(
                    assessment, lecturer, student, "Individual Assessment");
                Map<Integer, String> studentComments = lecturerAssessmentService.getExistingComments(
                    assessment, lecturer, student, "Individual Assessment");
                
                existingIndividualMarks.put(student.getId(), studentMarks);
                existingIndividualComments.put(student.getId(), studentComments);
                
                Map<Long, Map<Integer, String>> studentRubricComments = new HashMap<>();
                for (Rubric rubric : individualRubrics) {
                    if (rubric.getRubricCommentCount() != null && rubric.getRubricCommentCount() > 0) {
                        Map<Integer, String> rubricComments = lecturerAssessmentService.getExistingRubricComments(
                            assessment, lecturer, student, rubric.getId());
                        studentRubricComments.put(rubric.getId(), rubricComments);
                    }
                }
                existingIndividualRubricComments.put(student.getId(), studentRubricComments);
            }
        }

        model.addAttribute("assessment", assessment);
        model.addAttribute("selectedGroup", selectedGroup);
        model.addAttribute("groupStudents", groupStudents);
        model.addAttribute("groupRubrics", groupRubrics);
        model.addAttribute("individualRubrics", individualRubrics);
        model.addAttribute("existingGroupMarks", existingGroupMarks);
        model.addAttribute("existingGroupComments", existingGroupComments);
        model.addAttribute("existingIndividualMarks", existingIndividualMarks);
        model.addAttribute("existingIndividualComments", existingIndividualComments);
        model.addAttribute("existingGroupRubricComments", existingGroupRubricComments);
        model.addAttribute("existingIndividualRubricComments", existingIndividualRubricComments);

        return "lecturer_combined_evaluation_form";
    }

    @PostMapping("/{assessmentId}/submit")
    public String submitCombinedEvaluation(
            @PathVariable Long assessmentId,
            @RequestParam Long groupId,
            @RequestParam Map<String, String> allParams,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        Lecturer lecturer = getCurrentLecturer(authentication);

        try {
            Assessment assessment = rubricService.findAssessmentById(assessmentId);
            List<Student> groupStudents = lecturerAssessmentService.getStudentsByGroup(groupId);
            
            Map<String, String> groupScores = allParams.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("group_subRubric_") || 
                                entry.getKey().startsWith("group_rubric_"))
                .filter(entry -> !entry.getKey().contains("_comment_")) 
                .collect(Collectors.toMap(
                    e -> e.getKey().substring(6),
                    Map.Entry::getValue));
            
            Map<Integer, String> groupComments = new HashMap<>();
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                if (entry.getKey().startsWith("group_comment_")) {
                    try {
                        int index = Integer.parseInt(entry.getKey().substring(14));
                        groupComments.put(index, entry.getValue());
                    } catch (NumberFormatException e) {
                       
                    }
                }
            }
            
            Map<Long, Map<Integer, String>> groupRubricComments = new HashMap<>();
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                if (entry.getKey().startsWith("group_rubric_") && entry.getKey().contains("_comment_")) {
                    try {
                        String key = entry.getKey().substring(13); 
                        String[] parts = key.split("_comment_");
                        if (parts.length == 2) {
                            Long rubricId = Long.parseLong(parts[0]);
                            Integer commentIndex = Integer.parseInt(parts[1]);
                            
                            groupRubricComments.computeIfAbsent(rubricId, k -> new HashMap<>())
                                .put(commentIndex, entry.getValue());
                        }
                    } catch (NumberFormatException e) {
                      
                    }
                }
            }
            
            if (!groupScores.isEmpty() || !groupComments.isEmpty() || !groupRubricComments.isEmpty()) {
                lecturerAssessmentService.saveEvaluationScores(
                    assessmentId,
                    lecturer.getId(),
                    lecturer.getEmail(),
                    groupId,
                    true,
                    "Group Assessment",
                    groupScores,
                    groupComments,
                    groupRubricComments
                );
            }
            
            for (Student student : groupStudents) {
                String studentPrefix = "student_" + student.getId() + "_";
                
                Map<String, String> studentScores = allParams.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(studentPrefix + "subRubric_") || 
                                    entry.getKey().startsWith(studentPrefix + "rubric_"))
                    .filter(entry -> !entry.getKey().contains("_comment_")) 
                    .collect(Collectors.toMap(
                        e -> e.getKey().substring(studentPrefix.length()),
                        Map.Entry::getValue));
                
                Map<Integer, String> studentComments = new HashMap<>();
                for (Map.Entry<String, String> entry : allParams.entrySet()) {
                    if (entry.getKey().startsWith(studentPrefix + "comment_")) {
                        try {
                            int index = Integer.parseInt(entry.getKey().substring((studentPrefix + "comment_").length()));
                            studentComments.put(index, entry.getValue());
                        } catch (NumberFormatException e) {
                           
                        }
                    }
                }
                
                Map<Long, Map<Integer, String>> studentRubricComments = new HashMap<>();
                for (Map.Entry<String, String> entry : allParams.entrySet()) {
                    if (entry.getKey().startsWith(studentPrefix + "rubric_") && 
                        entry.getKey().contains("_comment_")) {
                        try {
                            String key = entry.getKey().substring(studentPrefix.length() + 7); 
                            String[] parts = key.split("_comment_");
                            if (parts.length == 2) {
                                Long rubricId = Long.parseLong(parts[0]);
                                Integer commentIndex = Integer.parseInt(parts[1]);
                                
                                studentRubricComments.computeIfAbsent(rubricId, k -> new HashMap<>())
                                    .put(commentIndex, entry.getValue());
                            }
                        } catch (NumberFormatException e) {
                           
                        }
                    }
                }
                
                if (!studentScores.isEmpty() || !studentComments.isEmpty() || !studentRubricComments.isEmpty()) {
                    lecturerAssessmentService.saveEvaluationScores(
                        assessmentId,
                        lecturer.getId(),
                        lecturer.getEmail(),
                        student.getId(),
                        false,
                        "Individual Assessment",
                        studentScores,
                        studentComments,
                        studentRubricComments
                    );
                }
            }

            boolean isComplete = lecturerAssessmentService.isGroupEvaluationComplete(
                assessment, lecturer, groupId);
            
            if (isComplete) {
                redirectAttributes.addFlashAttribute("successMessage", 
                    "✓ Evaluation completed successfully for all students in the group!");
            } else {
                redirectAttributes.addFlashAttribute("successMessage", 
                    "✓ Progress saved! Your evaluation is incomplete. Please complete all rubrics and comments to finish.");
            }
            
        } catch (Exception e) {
            logger.error("Error submitting combined evaluation for Assessment ID {}: {}", assessmentId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error saving evaluation: " + e.getMessage());
            
            return "redirect:/lecturer/assessments/" + assessmentId + "/evaluate?groupId=" + groupId;
        }

        return "redirect:/lecturer/assessments/" + assessmentId + "/select-group";
    }
}