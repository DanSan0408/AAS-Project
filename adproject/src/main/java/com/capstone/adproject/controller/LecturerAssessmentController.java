package com.capstone.adproject.controller;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
import com.capstone.adproject.model.Criteria;
import com.capstone.adproject.model.Deadline;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
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

    public LecturerAssessmentController(
            LecturerAssessmentService lecturerAssessmentService,
            RubricService rubricService,
            DeadlineService deadlineService) {
        this.lecturerAssessmentService = lecturerAssessmentService;
        this.rubricService = rubricService;
        this.deadlineService = deadlineService;
    }

    /**
     * Display list of assessments available for lecturer evaluation
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
     * STEP 1: Show selection page for evaluation type target (Group or Student)
     */
    @GetMapping("/{assessmentId}/select-target")
    public String showTargetSelection(
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
        Lecturer lecturer = new Lecturer();
        lecturer.setId((long) authentication.getName().hashCode());

        // Determine evaluation types
        boolean hasGroupEvaluation = assessment.getRubrics().stream()
            .anyMatch(r -> r.getEvaluationType() != null && 
                          r.getEvaluationType().toLowerCase().contains("group")) ||
            assessment.getCriteria().stream()
            .anyMatch(c -> c.getEvaluationType() != null && 
                          c.getEvaluationType().toLowerCase().contains("group"));

        boolean hasIndividualEvaluation = assessment.getRubrics().stream()
            .anyMatch(r -> r.getEvaluationType() != null && 
                          r.getEvaluationType().toLowerCase().contains("individual")) ||
            assessment.getCriteria().stream()
            .anyMatch(c -> c.getEvaluationType() != null && 
                          c.getEvaluationType().toLowerCase().contains("individual"));

        // Get all groups and students
        List<Group> allGroups = lecturerAssessmentService.getAllGroups();
        List<Student> allStudents = lecturerAssessmentService.getAllStudents();

        // Mark which targets have been evaluated
        Map<Long, Boolean> groupEvaluationStatus = new HashMap<>();
        for (Group group : allGroups) {
            boolean evaluated = lecturerAssessmentService
                .hasGroupBeenEvaluatedForGroupEvaluationAndGroupAssessment(assessment, group, lecturer);
            groupEvaluationStatus.put(group.getId(), evaluated);
        }

        Map<Long, Boolean> studentEvaluationStatus = new HashMap<>();
        for (Student student : allStudents) {
            boolean evaluated = lecturerAssessmentService
                .hasStudentBeenEvaluatedForIndividualEvaluationAndIndividualAssessment(assessment, student, lecturer);
            studentEvaluationStatus.put(student.getId(), evaluated);
        }

        model.addAttribute("assessment", assessment);
        model.addAttribute("hasGroupEvaluation", hasGroupEvaluation);
        model.addAttribute("hasIndividualEvaluation", hasIndividualEvaluation);
        model.addAttribute("allGroups", allGroups);
        model.addAttribute("allStudents", allStudents);
        model.addAttribute("groupEvaluationStatus", groupEvaluationStatus);
        model.addAttribute("studentEvaluationStatus", studentEvaluationStatus);

        return "lecturer_select_target";
    }

    /**
     * STEP 2: Confirm selection and show re-evaluation warning if needed
     */
    @PostMapping("/{assessmentId}/confirm-target")
    public String confirmTarget(
            @PathVariable Long assessmentId,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Boolean confirmReevaluation,
            Model model,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        Assessment assessment = rubricService.findAssessmentById(assessmentId);
        Lecturer lecturer = new Lecturer();
        lecturer.setId((long) authentication.getName().hashCode());

        boolean alreadyEvaluated = false;
        String targetType = null;
        Group selectedGroup = null;
        Student selectedStudent = null;

        if (groupId != null) {
            selectedGroup = lecturerAssessmentService.getAllGroups().stream()
                .filter(g -> g.getId().equals(groupId))
                .findFirst()
                .orElse(null);
            
            if (selectedGroup != null) {
                alreadyEvaluated = lecturerAssessmentService
                    .hasGroupBeenEvaluatedForGroupEvaluationAndGroupAssessment(
                        assessment, selectedGroup, lecturer);
                targetType = "GROUP";
            }
        } else if (studentId != null) {
            selectedStudent = lecturerAssessmentService.getAllStudents().stream()
                .filter(s -> s.getId().equals(studentId))
                .findFirst()
                .orElse(null);
            
            if (selectedStudent != null) {
                alreadyEvaluated = lecturerAssessmentService
                    .hasStudentBeenEvaluatedForIndividualEvaluationAndIndividualAssessment(
                        assessment, selectedStudent, lecturer);
                targetType = "STUDENT";
            }
        }

        // If already evaluated and not confirmed, show warning
        if (alreadyEvaluated && (confirmReevaluation == null || !confirmReevaluation)) {
            model.addAttribute("assessment", assessment);
            model.addAttribute("selectedGroup", selectedGroup);
            model.addAttribute("selectedStudent", selectedStudent);
            model.addAttribute("targetType", targetType);
            model.addAttribute("showReevaluationWarning", true);
            return "lecturer_reevaluation_warning";
        }

        // Proceed to evaluation form
        String redirectUrl = "/lecturer/assessments/" + assessmentId + "/evaluate?";
        if (groupId != null) {
            redirectUrl += "groupId=" + groupId;
        } else if (studentId != null) {
            redirectUrl += "studentId=" + studentId;
        }
        
        return "redirect:" + redirectUrl;
    }

/**
     * STEP 3: Show evaluation form - PAGE 1 (Evaluation Type + Same Assessment Type)
     */
    @GetMapping("/{assessmentId}/evaluate")
    public String showEvaluationForm(
            @PathVariable Long assessmentId,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Long studentId,
            Model model,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        Assessment assessment = rubricService.findAssessmentById(assessmentId);
        Lecturer lecturer = new Lecturer();
        lecturer.setId((long) authentication.getName().hashCode());

        Group selectedGroup = null;
        Student selectedStudent = null;
        String evaluationType = null;

        if (groupId != null) {
            selectedGroup = lecturerAssessmentService.getAllGroups().stream()
                .filter(g -> g.getId().equals(groupId))
                .findFirst()
                .orElse(null);
            evaluationType = "GROUP";
        } else if (studentId != null) {
            selectedStudent = lecturerAssessmentService.getAllStudents().stream()
                .filter(s -> s.getId().equals(studentId))
                .findFirst()
                .orElse(null);
            evaluationType = "INDIVIDUAL";
        }

        if (selectedGroup == null && selectedStudent == null) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Please select a group or student to evaluate.");
            return "redirect:/lecturer/assessments/" + assessmentId + "/select-target";
        }

        // PAGE 1: Get rubrics/criteria where EVALUATION TYPE = ASSESSMENT TYPE
        List<Rubric> evaluationRubrics = new ArrayList<>();
        List<Criteria> evaluationCriteria = new ArrayList<>();

        if ("GROUP".equals(evaluationType)) {
            // Group Evaluation + Group Assessment ONLY
            evaluationRubrics = assessment.getRubrics().stream()
                .filter(r -> r.getEvaluationType() != null && 
                            r.getEvaluationType().toLowerCase().contains("group") &&
                            r.getAssessmentTypes() != null &&
                            r.getAssessmentTypes().toLowerCase().contains("group"))
                .collect(Collectors.toList());
            
            evaluationCriteria = assessment.getCriteria().stream()
                .filter(c -> c.getEvaluationType() != null && 
                            c.getEvaluationType().toLowerCase().contains("group") &&
                            c.getAssessmentTypes() != null &&
                            c.getAssessmentTypes().toLowerCase().contains("group"))
                .collect(Collectors.toList());
        } else {
            // Individual Evaluation + Individual Assessment ONLY
            evaluationRubrics = assessment.getRubrics().stream()
                .filter(r -> r.getEvaluationType() != null && 
                            r.getEvaluationType().toLowerCase().contains("individual") &&
                            r.getAssessmentTypes() != null &&
                            r.getAssessmentTypes().toLowerCase().contains("individual"))
                .collect(Collectors.toList());
            
            evaluationCriteria = assessment.getCriteria().stream()
                .filter(c -> c.getEvaluationType() != null && 
                            c.getEvaluationType().toLowerCase().contains("individual") &&
                            c.getAssessmentTypes() != null &&
                            c.getAssessmentTypes().toLowerCase().contains("individual"))
                .collect(Collectors.toList());
        }

        model.addAttribute("assessment", assessment);
        model.addAttribute("selectedGroup", selectedGroup);
        model.addAttribute("selectedStudent", selectedStudent);
        model.addAttribute("evaluationType", evaluationType);
        model.addAttribute("evaluationRubrics", evaluationRubrics);
        model.addAttribute("evaluationCriteria", evaluationCriteria);

        logger.info("===== PAGE 1: EVALUATION TYPE =====");
        logger.info("Evaluation Type: {}", evaluationType);
        logger.info("Evaluation Rubrics: {}", evaluationRubrics.size());
        logger.info("Evaluation Criteria: {}", evaluationCriteria.size());
        
        // Log each rubric to verify
        for (Rubric r : evaluationRubrics) {
            logger.info("Page 1 Rubric: {} | EvalType: {} | AssessType: {}", 
                r.getName(), r.getEvaluationType(), r.getAssessmentTypes());
        }

        return "lecturer_evaluation_page1";
    }

    /**
     * STEP 4: Submit evaluation (Page 1) and redirect to assessment (Page 2)
     */
    @PostMapping("/{assessmentId}/evaluate/submit")
    public String submitEvaluation(
            @PathVariable Long assessmentId,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Long studentId,
            @RequestParam Map<String, String> allParams,
            @RequestParam(required = false) String comments,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        Long lecturerId = (long) authentication.getName().hashCode();

        try {
            Assessment assessment = rubricService.findAssessmentById(assessmentId);
            String evaluationType = groupId != null ? "Group Evaluation" : "Individual Evaluation";
            String assessmentType = groupId != null ? "Group Assessment" : "Individual Assessment";
            
            // Save evaluation type scores
            if (groupId != null) {
                // Group Evaluation - save for all students in group
                Map<String, String> evaluationScores = allParams.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("eval_"))
                    .collect(Collectors.toMap(
                        e -> e.getKey().replace("eval_", ""),
                        Map.Entry::getValue
                    ));
                
                if (!evaluationScores.isEmpty()) {
                    lecturerAssessmentService.saveEvaluationScores(
                        assessmentId, lecturerId, groupId, true, 
                        evaluationType, assessmentType, evaluationScores, comments);
                }
            } else if (studentId != null) {
                // Individual Evaluation
                Map<String, String> evaluationScores = allParams.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("eval_"))
                    .collect(Collectors.toMap(
                        e -> e.getKey().replace("eval_", ""),
                        Map.Entry::getValue
                    ));
                
                if (!evaluationScores.isEmpty()) {
                    lecturerAssessmentService.saveEvaluationScores(
                        assessmentId, lecturerId, studentId, false, 
                        evaluationType, assessmentType, evaluationScores, comments);
                }
            }

            redirectAttributes.addFlashAttribute("successMessage", 
                "Evaluation saved! Now proceed to assessment.");
            
            // Redirect to assessment page (Page 2)
            String redirectUrl = "/lecturer/assessments/" + assessmentId + "/assess?";
            if (groupId != null) {
                redirectUrl += "groupId=" + groupId;
            } else if (studentId != null) {
                redirectUrl += "studentId=" + studentId;
            }
            
            return "redirect:" + redirectUrl;
            
        } catch (Exception e) {
            logger.error("Error submitting evaluation: {}", e.getMessage(), e); 
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error submitting evaluation: " + e.getMessage());
            
            String redirectUrl = "/lecturer/assessments/" + assessmentId + "/evaluate?";
            if (groupId != null) {
                redirectUrl += "groupId=" + groupId;
            } else if (studentId != null) {
                redirectUrl += "studentId=" + studentId;
            }
            return "redirect:" + redirectUrl;
        }
    }

    /**
     * STEP 5: Show assessment form - PAGE 2 (Assessment Type)
     */
    @GetMapping("/{assessmentId}/assess")
    public String showAssessmentForm(
            @PathVariable Long assessmentId,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Long studentId,
            Model model,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        Assessment assessment = rubricService.findAssessmentById(assessmentId);
        Lecturer lecturer = new Lecturer();
        lecturer.setId((long) authentication.getName().hashCode());

        Group selectedGroup = null;
        Student selectedStudent = null;
        String evaluationType = null;
        String assessmentType = null;

        if (groupId != null) {
            selectedGroup = lecturerAssessmentService.getAllGroups().stream()
                .filter(g -> g.getId().equals(groupId))
                .findFirst()
                .orElse(null);
            evaluationType = "GROUP";
            assessmentType = "INDIVIDUAL"; // Group Evaluation → Individual Assessment
        } else if (studentId != null) {
            selectedStudent = lecturerAssessmentService.getAllStudents().stream()
                .filter(s -> s.getId().equals(studentId))
                .findFirst()
                .orElse(null);
            evaluationType = "INDIVIDUAL";
            assessmentType = "GROUP"; // Individual Evaluation → Group Assessment
        }

        // PAGE 2: Get rubrics/criteria based on EVALUATION TYPE and opposite ASSESSMENT TYPE
        List<Rubric> assessmentRubrics = new ArrayList<>();
        List<Criteria> assessmentCriteria = new ArrayList<>();

        if ("GROUP".equals(evaluationType)) {
            // Get Group Evaluation rubrics with Individual Assessment type
            assessmentRubrics = assessment.getRubrics().stream()
                .filter(r -> r.getEvaluationType() != null && 
                            r.getEvaluationType().toLowerCase().contains("group") &&
                            r.getAssessmentTypes() != null &&
                            r.getAssessmentTypes().toLowerCase().contains("individual"))
                .collect(Collectors.toList());
            
            assessmentCriteria = assessment.getCriteria().stream()
                .filter(c -> c.getEvaluationType() != null && 
                            c.getEvaluationType().toLowerCase().contains("group") &&
                            c.getAssessmentTypes() != null &&
                            c.getAssessmentTypes().toLowerCase().contains("individual"))
                .collect(Collectors.toList());
        } else {
            // Get Individual Evaluation rubrics with Group Assessment type
            assessmentRubrics = assessment.getRubrics().stream()
                .filter(r -> r.getEvaluationType() != null && 
                            r.getEvaluationType().toLowerCase().contains("individual") &&
                            r.getAssessmentTypes() != null &&
                            r.getAssessmentTypes().toLowerCase().contains("group"))
                .collect(Collectors.toList());
            
            assessmentCriteria = assessment.getCriteria().stream()
                .filter(c -> c.getEvaluationType() != null && 
                            c.getEvaluationType().toLowerCase().contains("individual") &&
                            c.getAssessmentTypes() != null &&
                            c.getAssessmentTypes().toLowerCase().contains("group"))
                .collect(Collectors.toList());
        }

        // Get students or groups for assessment
        List<Student> studentsToAssess = new ArrayList<>();
        List<Group> groupsToAssess = new ArrayList<>();

        if ("GROUP".equals(evaluationType)) {
            // Group Evaluation → Individual Assessment for each student
            studentsToAssess = lecturerAssessmentService.getStudentsByGroup(groupId);
        } else {
            // Individual Evaluation → Group Assessment for student's group
            if (selectedStudent != null && selectedStudent.getGroup() != null) {
                groupsToAssess.add(selectedStudent.getGroup());
            }
        }

        model.addAttribute("assessment", assessment);
        model.addAttribute("selectedGroup", selectedGroup);
        model.addAttribute("selectedStudent", selectedStudent);
        model.addAttribute("evaluationType", evaluationType);
        model.addAttribute("assessmentType", assessmentType);
        model.addAttribute("assessmentRubrics", assessmentRubrics);
        model.addAttribute("assessmentCriteria", assessmentCriteria);
        model.addAttribute("studentsToAssess", studentsToAssess);
        model.addAttribute("groupsToAssess", groupsToAssess);

        logger.info("===== PAGE 2: ASSESSMENT TYPE =====");
        logger.info("Evaluation Type: {}", evaluationType);
        logger.info("Assessment Type: {}", assessmentType);
        logger.info("Assessment Rubrics: {}", assessmentRubrics.size());
        logger.info("Assessment Criteria: {}", assessmentCriteria.size());
        logger.info("Students to Assess: {}", studentsToAssess.size());
        logger.info("Groups to Assess: {}", groupsToAssess.size());

        return "lecturer_assessment_page2";
    }

    /**
     * STEP 6: Submit assessment (Page 2) and complete
     */
    @PostMapping("/{assessmentId}/assess/submit")
    public String submitAssessment(
            @PathVariable Long assessmentId,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Long studentId,
            @RequestParam Map<String, String> allParams,
            @RequestParam(required = false) String comments,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        Long lecturerId = (long) authentication.getName().hashCode();

        try {
            if (groupId != null) {
                // Group Evaluation → Individual Assessment for each student
                List<Student> studentsInGroup = lecturerAssessmentService.getStudentsByGroup(groupId);
                
                for (Student student : studentsInGroup) {
                    Map<String, String> studentScores = allParams.entrySet().stream()
                        .filter(entry -> entry.getKey().startsWith("assess_student_" + student.getId() + "_"))
                        .collect(Collectors.toMap(
                            e -> e.getKey().replace("assess_student_" + student.getId() + "_", ""),
                            Map.Entry::getValue
                        ));
                    
                    if (!studentScores.isEmpty()) {
                        lecturerAssessmentService.saveEvaluationScores(
                            assessmentId, lecturerId, student.getId(), false, 
                            "Group Evaluation", "Individual Assessment", studentScores, comments);
                    }
                }
            } else if (studentId != null) {
                // Individual Evaluation → Group Assessment
                Student student = lecturerAssessmentService.getAllStudents().stream()
                    .filter(s -> s.getId().equals(studentId))
                    .findFirst()
                    .orElse(null);
                
                if (student != null && student.getGroup() != null) {
                    Map<String, String> groupScores = allParams.entrySet().stream()
                        .filter(entry -> entry.getKey().startsWith("assess_group_"))
                        .collect(Collectors.toMap(
                            e -> e.getKey().replace("assess_group_", ""),
                            Map.Entry::getValue
                        ));
                    
                    if (!groupScores.isEmpty()) {
                        lecturerAssessmentService.saveEvaluationScores(
                            assessmentId, lecturerId, student.getGroup().getId(), true, 
                            "Individual Evaluation", "Group Assessment", groupScores, comments);
                    }
                }
            }

            redirectAttributes.addFlashAttribute("successMessage", 
                "Assessment completed successfully!");
            
        } catch (Exception e) {
            logger.error("Error submitting assessment: {}", e.getMessage(), e); 
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error submitting assessment: " + e.getMessage());
        }

        return "redirect:/lecturer/assessments/" + assessmentId + "/select-target";
    }
    /**
     * STEP 4: Submit evaluation
     */
    @PostMapping("/{assessmentId}/submit")
    public String submitEvaluation(
            @PathVariable Long assessmentId,
            @RequestParam(required = false) Long evaluationGroupId,
            @RequestParam(required = false) Long evaluationStudentId,
            @RequestParam(required = false) Long assessmentGroupId,
            @RequestParam Map<String, String> allParams,
            @RequestParam(required = false) String comments,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        Long lecturerId = (long) authentication.getName().hashCode();

        try {
            Assessment assessment = rubricService.findAssessmentById(assessmentId);
            
            // PART 1: Process evaluation type scores (Group Evaluation or Individual Evaluation)
            if (evaluationGroupId != null) {
                // Group Evaluation - Group Assessment
                Map<String, String> evaluationScores = allParams.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("eval_group_"))
                    .collect(Collectors.toMap(
                        e -> e.getKey().replace("eval_group_", ""),
                        Map.Entry::getValue
                    ));
                
                if (!evaluationScores.isEmpty()) {
                    lecturerAssessmentService.saveEvaluationScores(
                        assessmentId, lecturerId, evaluationGroupId, true, 
                        "Group Evaluation", "Group Assessment", evaluationScores, comments);
                }
            } else if (evaluationStudentId != null) {
                // Individual Evaluation - Individual Assessment
                Map<String, String> evaluationScores = allParams.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("eval_student_"))
                    .collect(Collectors.toMap(
                        e -> e.getKey().replace("eval_student_", ""),
                        Map.Entry::getValue
                    ));
                
                if (!evaluationScores.isEmpty()) {
                    lecturerAssessmentService.saveEvaluationScores(
                        assessmentId, lecturerId, evaluationStudentId, false, 
                        "Individual Evaluation", "Individual Assessment", evaluationScores, comments);
                }
            }

            // PART 2: Process assessment type scores
            
            // Scenario A: Group Assessment (from Individual Evaluation)
            if (assessmentGroupId != null) {
                Map<String, String> assessmentScores = allParams.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("assess_group_"))
                    .collect(Collectors.toMap(
                        e -> e.getKey().replace("assess_group_", ""),
                        Map.Entry::getValue
                    ));
                
                if (!assessmentScores.isEmpty()) {
                    String evalType = evaluationStudentId != null ? "Individual Evaluation" : "Group Evaluation";
                    lecturerAssessmentService.saveEvaluationScores(
                        assessmentId, lecturerId, assessmentGroupId, true, 
                        evalType, "Group Assessment", assessmentScores, comments);
                }
            }
            
            // Scenario B: Individual Assessment for each student (from Group Evaluation)
            if (evaluationGroupId != null) {
                // Get all students in the group
                List<Student> studentsInGroup = lecturerAssessmentService.getStudentsByGroup(evaluationGroupId);
                
                // Process each student's individual assessment
                for (Student student : studentsInGroup) {
                    Map<String, String> studentScores = allParams.entrySet().stream()
                        .filter(entry -> entry.getKey().startsWith("assess_student_" + student.getId() + "_"))
                        .collect(Collectors.toMap(
                            e -> e.getKey().replace("assess_student_" + student.getId() + "_", ""),
                            Map.Entry::getValue
                        ));
                    
                    if (!studentScores.isEmpty()) {
                        lecturerAssessmentService.saveEvaluationScores(
                            assessmentId, lecturerId, student.getId(), false, 
                            "Group Evaluation", "Individual Assessment", studentScores, comments);
                    }
                }
            }

            redirectAttributes.addFlashAttribute("successMessage", 
                "Evaluation submitted successfully!");
            
        } catch (Exception e) {
            logger.error("Error submitting evaluation for Assessment ID {}: {}", assessmentId, e.getMessage(), e); 
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error submitting evaluation: " + e.getMessage());
        }

        return "redirect:/lecturer/assessments/" + assessmentId + "/select-target";
    }
}