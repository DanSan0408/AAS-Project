package com.capstone.adproject.controller;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import com.capstone.adproject.repositories.LecturerRepository;
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

    public LecturerAssessmentController(
            LecturerAssessmentService lecturerAssessmentService,
            RubricService rubricService,
            DeadlineService deadlineService,
            LecturerRepository lecturerRepository) {
        this.lecturerAssessmentService = lecturerAssessmentService;
        this.rubricService = rubricService;
        this.deadlineService = deadlineService;
        this.lecturerRepository = lecturerRepository;
    }

    /**
     * Helper method to get current lecturer from authentication
     */
    private Lecturer getCurrentLecturer(Authentication authentication) {
        String username = authentication.getName();
        return lecturerRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("Lecturer not found: " + username));
    }

    /**
     * STEP 1: Display list of assessments available for lecturer evaluation
     */
    @GetMapping
    public String showAssessments(Model model, Authentication authentication) {
        List<Assessment> assessments = lecturerAssessmentService.getAssessmentsForLecturerEvaluation();
        
        LocalDateTime now = LocalDateTime.now();
        List<Long> openAssessmentIds = assessments.stream()
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
        
        model.addAttribute("assessments", assessments);
        model.addAttribute("openAssessmentIds", openAssessmentIds);
        
        return "lecturer_assessments";
    }

    /**
     * STEP 2: NEW - Select rubric type (Group Assessment or Individual Assessment)
     */
    @GetMapping("/{assessmentId}/select-rubric-type")
    public String showRubricTypeSelection(
            @PathVariable Long assessmentId,
            Model model,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        // Check if assessment is open
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
        
        // Check which rubric types are available
        boolean hasGroupRubrics = assessment.getRubrics().stream()
            .anyMatch(r -> r.getAssessmentTypes() != null && 
                          r.getAssessmentTypes().equalsIgnoreCase("Group Assessment"));

        boolean hasIndividualRubrics = assessment.getRubrics().stream()
            .anyMatch(r -> r.getAssessmentTypes() != null && 
                          r.getAssessmentTypes().equalsIgnoreCase("Individual Assessment"));

        // Check if assessment has any rubrics at all
        if (!hasGroupRubrics && !hasIndividualRubrics) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "This assessment has no rubrics configured.");
            return "redirect:/lecturer/assessments";
        }
        
        model.addAttribute("assessment", assessment);
        model.addAttribute("hasGroupRubrics", hasGroupRubrics);
        model.addAttribute("hasIndividualRubrics", hasIndividualRubrics);

        return "lecturer_select_rubric_type";
    }

    /**
     * STEP 3: Show target selection page - groups or students based on selected rubric type
     */
    @GetMapping("/{assessmentId}/select-target")
    public String showTargetSelection(
            @PathVariable Long assessmentId,
            @RequestParam String rubricType,
            Model model,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        Assessment assessment = rubricService.findAssessmentById(assessmentId);
        Lecturer lecturer = getCurrentLecturer(authentication);

        boolean isGroupAssessment = rubricType.equalsIgnoreCase("Group Assessment");

        // Get rubrics for this specific type
        List<Rubric> relevantRubrics = assessment.getRubrics().stream()
            .filter(r -> r.getAssessmentTypes() != null && 
                        r.getAssessmentTypes().equalsIgnoreCase(rubricType))
            .collect(Collectors.toList());

        if (relevantRubrics.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "No rubrics found for " + rubricType + ".");
            return "redirect:/lecturer/assessments/" + assessmentId + "/select-rubric-type";
        }

        if (isGroupAssessment) {
            // Show groups
            List<Group> allGroups = lecturerAssessmentService.getAllGroups();
            Map<Long, Boolean> groupEvaluationStatus = new HashMap<>();
            
            for (Group group : allGroups) {
                boolean evaluated = lecturerAssessmentService.hasTargetBeenEvaluated(
                    assessment, lecturer, group.getId(), true, rubricType);
                groupEvaluationStatus.put(group.getId(), evaluated);
            }

            model.addAttribute("allGroups", allGroups);
            model.addAttribute("groupEvaluationStatus", groupEvaluationStatus);
        } else {
            // Show students
            List<Student> allStudents = lecturerAssessmentService.getAllStudents();
            Map<Long, Boolean> studentEvaluationStatus = new HashMap<>();
            
            for (Student student : allStudents) {
                boolean evaluated = lecturerAssessmentService.hasTargetBeenEvaluated(
                    assessment, lecturer, student.getId(), false, rubricType);
                studentEvaluationStatus.put(student.getId(), evaluated);
            }

            model.addAttribute("allStudents", allStudents);
            model.addAttribute("studentEvaluationStatus", studentEvaluationStatus);
        }

        model.addAttribute("assessment", assessment);
        model.addAttribute("rubricType", rubricType);
        model.addAttribute("isGroupAssessment", isGroupAssessment);

        return "lecturer_select_target";
    }

    /**
     * STEP 4: Show evaluation form with rubrics and comments
     */
    @GetMapping("/{assessmentId}/evaluate")
    public String showEvaluationForm(
            @PathVariable Long assessmentId,
            @RequestParam String rubricType,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Boolean confirmReevaluation,
            Model model,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        Assessment assessment = rubricService.findAssessmentById(assessmentId);
        Lecturer lecturer = getCurrentLecturer(authentication);

        Group selectedGroup = null;
        Student selectedStudent = null;
        boolean isGroupTarget = rubricType.equalsIgnoreCase("Group Assessment");

        // Validate target selection
        if (isGroupTarget) {
            if (groupId == null) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "Please select a group to evaluate.");
                return "redirect:/lecturer/assessments/" + assessmentId + 
                       "/select-target?rubricType=" + rubricType;
            }
            selectedGroup = lecturerAssessmentService.getAllGroups().stream()
                .filter(g -> g.getId().equals(groupId))
                .findFirst()
                .orElse(null);
            
            if (selectedGroup == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Group not found.");
                return "redirect:/lecturer/assessments/" + assessmentId + 
                       "/select-target?rubricType=" + rubricType;
            }
        } else {
            if (studentId == null) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "Please select a student to evaluate.");
                return "redirect:/lecturer/assessments/" + assessmentId + 
                       "/select-target?rubricType=" + rubricType;
            }
            selectedStudent = lecturerAssessmentService.getAllStudents().stream()
                .filter(s -> s.getId().equals(studentId))
                .findFirst()
                .orElse(null);
            
            if (selectedStudent == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Student not found.");
                return "redirect:/lecturer/assessments/" + assessmentId + 
                       "/select-target?rubricType=" + rubricType;
            }
        }

        // Check for re-evaluation
        Long targetId = isGroupTarget ? groupId : studentId;
        boolean alreadyEvaluated = lecturerAssessmentService.hasTargetBeenEvaluated(
            assessment, lecturer, targetId, isGroupTarget, rubricType);

        if (alreadyEvaluated && (confirmReevaluation == null || !confirmReevaluation)) {
            model.addAttribute("assessment", assessment);
            model.addAttribute("selectedGroup", selectedGroup);
            model.addAttribute("selectedStudent", selectedStudent);
            model.addAttribute("rubricType", rubricType);
            model.addAttribute("showReevaluationWarning", true);
            return "lecturer_reevaluation_warning";
        }

        // Get rubrics for this assessment type only
        List<Rubric> assessmentRubrics = assessment.getRubrics().stream()
            .filter(r -> r.getAssessmentTypes() != null && 
                        r.getAssessmentTypes().equalsIgnoreCase(rubricType))
            .collect(Collectors.toList());

        // Get students to evaluate (for group assessments, get all group members)
        List<Student> studentsToAssess = null;
        if (isGroupTarget && selectedGroup != null) {
            studentsToAssess = lecturerAssessmentService.getStudentsByGroup(groupId);
        }

        // Fetch existing marks and comments if re-evaluating
        Map<String, Long> existingMarks = new HashMap<>();
        Map<Integer, String> existingComments = new HashMap<>();
        
        if (alreadyEvaluated) {
            // Get existing marks from the first student (all in group have same marks)
            Student sampleStudent = isGroupTarget ? 
                (studentsToAssess != null && !studentsToAssess.isEmpty() ? studentsToAssess.get(0) : null) :
                selectedStudent;
            
            if (sampleStudent != null) {
                existingMarks = lecturerAssessmentService.getExistingMarks(
                    assessment, lecturer, sampleStudent, rubricType);
                existingComments = lecturerAssessmentService.getExistingComments(
                assessment, lecturer, sampleStudent, rubricType);
            }
        }

        model.addAttribute("assessment", assessment);
        model.addAttribute("selectedGroup", selectedGroup);
        model.addAttribute("selectedStudent", selectedStudent);
        model.addAttribute("isGroupTarget", isGroupTarget);
        model.addAttribute("rubricType", rubricType);
        model.addAttribute("assessmentRubrics", assessmentRubrics);
        model.addAttribute("studentsToAssess", studentsToAssess);
        model.addAttribute("existingMarks", existingMarks);
        model.addAttribute("existingComments", existingComments);

        return "lecturer_evaluation_form";
    }

    /**
     * STEP 5: Submit evaluation
     */
    @PostMapping("/{assessmentId}/submit")
    public String submitEvaluation(
            @PathVariable Long assessmentId,
            @RequestParam String rubricType,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Long studentId,
            @RequestParam Map<String, String> allParams,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        Lecturer lecturer = getCurrentLecturer(authentication);

        try {
            // Extract scores from params (both sub-rubric and direct rubric scores)
            Map<String, String> scores = allParams.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("subRubric_") || 
                                entry.getKey().startsWith("rubric_"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            
            // Extract comments (supporting multiple comment fields)
            Map<Integer, String> comments = new HashMap<>();
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                if (entry.getKey().startsWith("comment_")) {
                    try {
                        int index = Integer.parseInt(entry.getKey().substring(8));
                        comments.put(index, entry.getValue());
                    } catch (NumberFormatException e) {
                        // Skip invalid comment keys
                    }
                }
            }
            
            if (scores.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "No scores were submitted. Please select ratings for all rubrics.");
                
                String redirectUrl = "/lecturer/assessments/" + assessmentId + "/evaluate?rubricType=" + rubricType;
                if (groupId != null) {
                    redirectUrl += "&groupId=" + groupId;
                } else if (studentId != null) {
                    redirectUrl += "&studentId=" + studentId;
                }
                return "redirect:" + redirectUrl;
            }

            // Determine target
            Long targetId = groupId != null ? groupId : studentId;
            boolean isGroupTarget = groupId != null;

            // Save the evaluation - pass lecturer object with username
            lecturerAssessmentService.saveEvaluationScores(
                assessmentId, 
                lecturer.getId(),
                lecturer.getUsername(),  // ✅ PASS LECTURER NAME
                targetId, 
                isGroupTarget,
                rubricType,
                scores, 
                comments
            );

            redirectAttributes.addFlashAttribute("successMessage", 
                "Evaluation submitted successfully!");
            
        } catch (Exception e) {
            logger.error("Error submitting evaluation for Assessment ID {}: {}", assessmentId, e.getMessage(), e); 
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error submitting evaluation: " + e.getMessage());
            
            String redirectUrl = "/lecturer/assessments/" + assessmentId + "/evaluate?rubricType=" + rubricType;
            if (groupId != null) {
                redirectUrl += "&groupId=" + groupId;
            } else if (studentId != null) {
                redirectUrl += "&studentId=" + studentId;
            }
            return "redirect:" + redirectUrl;
        }

        return "redirect:/lecturer/assessments/" + assessmentId + "/select-rubric-type";
    }
}