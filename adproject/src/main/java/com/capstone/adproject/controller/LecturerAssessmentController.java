package com.capstone.adproject.controller;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import com.capstone.adproject.model.LecturerRubricAssignment;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.LecturerRubricAssignmentRepository;
import com.capstone.adproject.repositories.LecturerStudentAssignmentRepository;
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
    private final LecturerStudentAssignmentRepository studentAssignmentRepository;
    private final LecturerRubricAssignmentRepository rubricAssignmentRepository;
    private final GroupRepository groupRepository;

    public LecturerAssessmentController(
            LecturerAssessmentService lecturerAssessmentService,
            RubricService rubricService,
            DeadlineService deadlineService,
            LecturerRepository lecturerRepository,
            LecturerGroupAssignmentRepository assignmentRepository,
            LecturerStudentAssignmentRepository studentAssignmentRepository,
            LecturerRubricAssignmentRepository rubricAssignmentRepository,
            GroupRepository groupRepository) {
        this.lecturerAssessmentService = lecturerAssessmentService;
        this.rubricService = rubricService;
        this.deadlineService = deadlineService;
        this.lecturerRepository = lecturerRepository;
        this.assignmentRepository = assignmentRepository;
        this.studentAssignmentRepository = studentAssignmentRepository;
        this.rubricAssignmentRepository = rubricAssignmentRepository;
        this.groupRepository = groupRepository;
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
        List<Assessment> rubricAssessments = rubricAssignmentRepository.findAssessmentsByLecturer(lecturer);
        List<Assessment> studentAssessments = studentAssignmentRepository.findAssessmentsByLecturer(lecturer);
        Set<Long> studentAssessmentIds = studentAssessments.stream()
            .map(Assessment::getId)
            .collect(Collectors.toSet());
        
        Set<Assessment> combinedAssessments = new HashSet<>(assignedAssessments);
        combinedAssessments.addAll(rubricAssessments);
        combinedAssessments.addAll(studentAssessments);
        List<Assessment> uniqueAssessments = new ArrayList<>(combinedAssessments);

        Map<Long, String> assessmentLaunchModes = new HashMap<>();
        for (Assessment assessment : uniqueAssessments) {
            if (studentAssessmentIds.contains(assessment.getId())) {
                assessmentLaunchModes.put(assessment.getId(), "STUDENT");
            } else {
                assessmentLaunchModes.put(assessment.getId(), "GROUP");
            }
        }
        
        LocalDateTime now = LocalDateTime.now();
        List<Long> openAssessmentIds = uniqueAssessments.stream()
            .filter(assessment -> {
                List<Deadline> deadlines = deadlineService.getDeadlinesByAssessmentId(assessment.getId()).stream()
                    .filter(d -> d.getAssessorType() == null 
                              || "LECTURER".equalsIgnoreCase(d.getAssessorType()) 
                              || "GENERAL".equalsIgnoreCase(d.getAssessorType()) 
                              || "SUPERVISOR".equalsIgnoreCase(d.getAssessorType()))
                    .collect(Collectors.toList());
                
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
        
        model.addAttribute("assessments", uniqueAssessments);
        model.addAttribute("openAssessmentIds", openAssessmentIds);
        model.addAttribute("assessmentLaunchModes", assessmentLaunchModes);
        
        return "lecturer_assessments";
    }

    @GetMapping("/{assessmentId}/select-group")
    public String showGroupSelection(
            @PathVariable("assessmentId") Long assessmentId,
            Model model,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        Lecturer lecturer = getCurrentLecturer(authentication);
        
        List<Deadline> lecturerDeadlines = deadlineService.getDeadlinesByAssessmentId(assessmentId).stream()
            .filter(d -> d.getAssessorType() == null 
                      || "LECTURER".equalsIgnoreCase(d.getAssessorType()) 
                      || "GENERAL".equalsIgnoreCase(d.getAssessorType()) 
                      || "SUPERVISOR".equalsIgnoreCase(d.getAssessorType()))
            .collect(Collectors.toList());
        
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
        
        boolean hasRubricAssignment = rubricAssignmentRepository.existsByLecturerAndAssessment(lecturer, assessment);
        if (hasRubricAssignment) {
            List<Group> allGroupsWithStudents = groupRepository.findAllWithStudents();
            Set<Group> combinedGroups = new HashSet<>(assignedGroups);
            combinedGroups.addAll(allGroupsWithStudents);
            assignedGroups = new ArrayList<>(combinedGroups);
        }
        
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

    @GetMapping("/{assessmentId}/select-student")
    public String showStudentSelection(
            @PathVariable("assessmentId") Long assessmentId,
            Model model,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        Lecturer lecturer = getCurrentLecturer(authentication);

        List<Deadline> lecturerDeadlines = deadlineService.getDeadlinesByAssessmentId(assessmentId).stream()
            .filter(d -> d.getAssessorType() == null 
                      || "LECTURER".equalsIgnoreCase(d.getAssessorType()) 
                      || "GENERAL".equalsIgnoreCase(d.getAssessorType()) 
                      || "SUPERVISOR".equalsIgnoreCase(d.getAssessorType()))
            .collect(Collectors.toList());

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
        List<Student> assignedStudents = new ArrayList<>(studentAssignmentRepository.findStudentsByLecturerAndAssessment(lecturer, assessment));

        // Sort alphabetically by username (fallback to email)
        assignedStudents.sort((s1, s2) -> {
            String name1 = (s1.getUsername() != null && !s1.getUsername().trim().isEmpty()) ? s1.getUsername().trim() : (s1.getEmail() != null ? s1.getEmail() : "");
            String name2 = (s2.getUsername() != null && !s2.getUsername().trim().isEmpty()) ? s2.getUsername().trim() : (s2.getEmail() != null ? s2.getEmail() : "");
            return name1.compareToIgnoreCase(name2);
        });

        if (assignedStudents.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                "You have no students assigned for this assessment.");
            return "redirect:/lecturer/assessments";
        }

        Map<Long, String> studentEvaluationStatus = new HashMap<>();
        for (Student student : assignedStudents) {
            String status = lecturerAssessmentService.getStudentEvaluationStatus(
                assessment, lecturer, student.getId());
            studentEvaluationStatus.put(student.getId(), status);
        }

        model.addAttribute("assessment", assessment);
        model.addAttribute("assignedStudents", assignedStudents);
        model.addAttribute("studentEvaluationStatus", studentEvaluationStatus);

        return "lecturer_select_student";
    }

    @GetMapping("/{assessmentId}/evaluate")
    public String showCombinedEvaluationForm(
            @PathVariable("assessmentId") Long assessmentId,
            @RequestParam(value = "groupId", required = false) Long groupId,
            @RequestParam(value = "studentId", required = false) Long studentId,
            @RequestParam(value = "confirmReevaluation", required = false) Boolean confirmReevaluation,
            Model model,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        Assessment assessment = rubricService.findAssessmentById(assessmentId);
        Lecturer lecturer = getCurrentLecturer(authentication);

        boolean isStudentTarget = studentId != null;
        Group selectedGroup;
        List<Student> targetStudents;
        List<Rubric> groupRubrics;
        List<Rubric> individualRubrics;
        Map<String, Long> existingGroupMarks = new HashMap<>();
        Map<Integer, String> existingGroupComments = new HashMap<>();
        Map<Long, Map<String, Long>> existingIndividualMarks = new HashMap<>();
        Map<Long, Map<Integer, String>> existingIndividualComments = new HashMap<>();
        Map<Long, Map<Integer, String>> existingGroupRubricComments = new HashMap<>();
        Map<Long, Map<Long, Map<Integer, String>>> existingIndividualRubricComments = new HashMap<>();

        if (isStudentTarget) {
            List<Student> assignedStudents = studentAssignmentRepository.findStudentsByLecturerAndAssessment(lecturer, assessment);
            Student selectedStudent = assignedStudents.stream()
                .filter(student -> student.getId().equals(studentId))
                .findFirst()
                .orElse(null);

            if (selectedStudent == null) {
                redirectAttributes.addFlashAttribute("errorMessage",
                    "You are not assigned to evaluate this student.");
                return "redirect:/lecturer/assessments/" + assessmentId + "/select-student";
            }

            String evaluationStatus = lecturerAssessmentService.getStudentEvaluationStatus(
                assessment, lecturer, studentId);

            targetStudents = List.of(selectedStudent);
            selectedGroup = new Group();
            selectedGroup.setId(selectedStudent.getId());
            selectedGroup.setGroupName(selectedStudent.getEmail());

            groupRubrics = assessment.getRubrics().stream()
                .filter(r -> r.getAssessmentTypes() != null &&
                             r.getAssessmentTypes().equalsIgnoreCase("Group Assessment"))
                .collect(Collectors.toList());

            individualRubrics = assessment.getRubrics().stream()
                .filter(r -> r.getAssessmentTypes() != null &&
                             r.getAssessmentTypes().equalsIgnoreCase("Individual Assessment"))
                .collect(Collectors.toList());

            if (!"not_started".equals(evaluationStatus) && !targetStudents.isEmpty()) {
                Student sampleStudent = targetStudents.get(0);

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

                Map<String, Long> studentMarks = lecturerAssessmentService.getExistingMarks(
                    assessment, lecturer, sampleStudent, "Individual Assessment");
                Map<Integer, String> studentComments = lecturerAssessmentService.getExistingComments(
                    assessment, lecturer, sampleStudent, "Individual Assessment");
                existingIndividualMarks.put(sampleStudent.getId(), studentMarks);
                existingIndividualComments.put(sampleStudent.getId(), studentComments);

                Map<Long, Map<Integer, String>> studentRubricComments = new HashMap<>();
                for (Rubric rubric : individualRubrics) {
                    if (rubric.getRubricCommentCount() != null && rubric.getRubricCommentCount() > 0) {
                        Map<Integer, String> rubricComments = lecturerAssessmentService.getExistingRubricComments(
                            assessment, lecturer, sampleStudent, rubric.getId());
                        studentRubricComments.put(rubric.getId(), rubricComments);
                    }
                }
                existingIndividualRubricComments.put(sampleStudent.getId(), studentRubricComments);
            }

            model.addAttribute("assessment", assessment);
            model.addAttribute("selectedGroup", selectedGroup);
            model.addAttribute("groupStudents", targetStudents);
            model.addAttribute("groupRubrics", groupRubrics);
            model.addAttribute("individualRubrics", individualRubrics);
            model.addAttribute("existingGroupMarks", existingGroupMarks);
            model.addAttribute("existingGroupComments", existingGroupComments);
            model.addAttribute("existingIndividualMarks", existingIndividualMarks);
            model.addAttribute("existingIndividualComments", existingIndividualComments);
            model.addAttribute("existingGroupRubricComments", existingGroupRubricComments);
            model.addAttribute("existingIndividualRubricComments", existingIndividualRubricComments);
            model.addAttribute("isAssignedToGroup", true);
            model.addAttribute("isStudentTarget", true);

            return "lecturer_combined_evaluation_form";
        }

        List<Group> assignedGroups = assignmentRepository.findGroupsByLecturerAndAssessment(lecturer, assessment);

        boolean hasRubricAssignment = rubricAssignmentRepository.existsByLecturerAndAssessment(lecturer, assessment);
        if (hasRubricAssignment) {
            List<Group> allGroupsWithStudents = groupRepository.findAllWithStudents();
            Set<Group> combinedGroups = new HashSet<>(assignedGroups);
            combinedGroups.addAll(allGroupsWithStudents);
            assignedGroups = new ArrayList<>(combinedGroups);
        }

        Group targetGroup = assignedGroups.stream()
            .filter(g -> g.getId().equals(groupId))
            .findFirst()
            .orElse(null);

        if (targetGroup == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                "You are not assigned to evaluate this group.");
            return "redirect:/lecturer/assessments/" + assessmentId + "/select-group";
        }

        String evaluationStatus = lecturerAssessmentService.getGroupEvaluationStatus(
            assessment, lecturer, groupId);

        boolean isComplete = "completed".equals(evaluationStatus);

        if (isComplete && (confirmReevaluation == null || !confirmReevaluation)) {
            model.addAttribute("assessment", assessment);
            model.addAttribute("selectedGroup", targetGroup);
            model.addAttribute("showReevaluationWarning", true);
            return "lecturer_reevaluation_warning_combined";
        }

        boolean isAssignedToGroup = assignmentRepository.existsByAssessmentAndGroupAndLecturer(assessment, targetGroup, lecturer);

        List<LecturerRubricAssignment> rubricAssignments = rubricAssignmentRepository.findByLecturerAndAssessment(lecturer, assessment);
        Set<Long> allowedRubricIds = rubricAssignments.stream()
            .map(a -> a.getRubric().getId())
            .collect(Collectors.toSet());

        groupRubrics = assessment.getRubrics().stream()
            .filter(r -> r.getAssessmentTypes() != null &&
                        r.getAssessmentTypes().equalsIgnoreCase("Group Assessment"))
            .filter(r -> isAssignedToGroup || allowedRubricIds.contains(r.getId()))
            .collect(Collectors.toList());

        individualRubrics = assessment.getRubrics().stream()
            .filter(r -> r.getAssessmentTypes() != null &&
                        r.getAssessmentTypes().equalsIgnoreCase("Individual Assessment"))
            .filter(r -> isAssignedToGroup || allowedRubricIds.contains(r.getId()))
            .collect(Collectors.toList());

        targetStudents = lecturerAssessmentService.getStudentsByGroup(groupId);

        if (!"not_started".equals(evaluationStatus) && !targetStudents.isEmpty()) {
            Student sampleStudent = targetStudents.get(0);

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

            for (Student student : targetStudents) {
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
        model.addAttribute("selectedGroup", targetGroup);
        model.addAttribute("groupStudents", targetStudents);
        model.addAttribute("groupRubrics", groupRubrics);
        model.addAttribute("individualRubrics", individualRubrics);
        model.addAttribute("existingGroupMarks", existingGroupMarks);
        model.addAttribute("existingGroupComments", existingGroupComments);
        model.addAttribute("existingIndividualMarks", existingIndividualMarks);
        model.addAttribute("existingIndividualComments", existingIndividualComments);
        model.addAttribute("existingGroupRubricComments", existingGroupRubricComments);
        model.addAttribute("existingIndividualRubricComments", existingIndividualRubricComments);
        model.addAttribute("isAssignedToGroup", isAssignedToGroup);
        model.addAttribute("isStudentTarget", false);

        return "lecturer_combined_evaluation_form";
    }

    @PostMapping("/{assessmentId}/submit")
    public String submitCombinedEvaluation(
            @PathVariable("assessmentId") Long assessmentId,
            @RequestParam(value = "groupId", required = false) Long groupId,
            @RequestParam(value = "studentId", required = false) Long studentId,
            @RequestParam Map<String, String> allParams,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        Lecturer lecturer = getCurrentLecturer(authentication);

        try {
            Assessment assessment = rubricService.findAssessmentById(assessmentId);
            boolean isStudentTarget = studentId != null;

            if (isStudentTarget) {
                List<Student> assignedStudents = studentAssignmentRepository.findStudentsByLecturerAndAssessment(lecturer, assessment);
                Student selectedStudent = assignedStudents.stream()
                    .filter(student -> student.getId().equals(studentId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Student not found"));

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
                        selectedStudent.getId(),
                        false,
                        "Group Assessment",
                        groupScores,
                        groupComments,
                        groupRubricComments
                    );
                }

                Map<String, String> studentScores = allParams.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("student_" + studentId + "_subRubric_") ||
                                    entry.getKey().startsWith("student_" + studentId + "_rubric_"))
                    .filter(entry -> !entry.getKey().contains("_comment_"))
                    .collect(Collectors.toMap(
                        e -> e.getKey().substring(("student_" + studentId + "_").length()),
                        Map.Entry::getValue));

                Map<Integer, String> studentComments = new HashMap<>();
                String studentPrefix = "student_" + studentId + "_";
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
                        selectedStudent.getId(),
                        false,
                        "Individual Assessment",
                        studentScores,
                        studentComments,
                        studentRubricComments
                    );
                }

                boolean isComplete = lecturerAssessmentService.isStudentEvaluationComplete(
                    assessment, lecturer, selectedStudent.getId());

                if (isComplete) {
                    redirectAttributes.addFlashAttribute("successMessage",
                        "✓ Evaluation completed successfully for the selected student!");
                } else {
                    redirectAttributes.addFlashAttribute("successMessage",
                        "✓ Progress saved! Your evaluation is incomplete. Please complete all rubrics and comments to finish.");
                }

                return "redirect:/lecturer/assessments/" + assessmentId + "/select-student";
            }

            Group selectedGroup = groupRepository.findById(groupId).orElseThrow(() -> new RuntimeException("Group not found"));
            boolean isAssignedToGroup = assignmentRepository.existsByAssessmentAndGroupAndLecturer(assessment, selectedGroup, lecturer);
            boolean hasRubricAssignment = rubricAssignmentRepository.existsByLecturerAndAssessment(lecturer, assessment);

            if (!isAssignedToGroup && !hasRubricAssignment) {
                redirectAttributes.addFlashAttribute("errorMessage", "Not authorized to evaluate this group.");
                return "redirect:/lecturer/assessments";
            }

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
            
            String redirectUrl = studentId != null
                ? "redirect:/lecturer/assessments/" + assessmentId + "/evaluate?studentId=" + studentId
                : "redirect:/lecturer/assessments/" + assessmentId + "/evaluate?groupId=" + groupId;
            return redirectUrl;
        }

        return studentId != null
            ? "redirect:/lecturer/assessments/" + assessmentId + "/select-student"
            : "redirect:/lecturer/assessments/" + assessmentId + "/select-group";
    }
}