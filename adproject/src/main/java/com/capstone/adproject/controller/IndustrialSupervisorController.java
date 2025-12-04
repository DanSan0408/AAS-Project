package com.capstone.adproject.controller;

import java.security.Principal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Deadline;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.IndustrialSupervisor;
import com.capstone.adproject.model.Rating;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.SubRubric;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.DeadlineService;
import com.capstone.adproject.service.IndustrialSupervisorService;

@Controller
@RequestMapping("/supervisor")
public class IndustrialSupervisorController {

    private final AssessmentService assessmentService;
    private final DeadlineService deadlineService;
    private final IndustrialSupervisorService industrialSupervisorService;

    public IndustrialSupervisorController(
            AssessmentService assessmentService,
            DeadlineService deadlineService,
            IndustrialSupervisorService industrialSupervisorService) {
        this.assessmentService = assessmentService;
        this.deadlineService = deadlineService;
        this.industrialSupervisorService = industrialSupervisorService;
    }

    @Transactional(readOnly = true)
    private Optional<Assessment> loadIndustrialSupervisorAssessment() {
        Optional<Assessment> assessmentOpt = assessmentService.findByTitle("Industrial Supervisor Assessment");
        if (assessmentOpt.isEmpty()) {
            return Optional.empty();
        }

        Assessment assessment = assessmentOpt.get();

        // Aggressively initialize ALL collections to ensure ratings are loaded
        if (assessment.getRubrics() != null) {
            for (Rubric rubric : assessment.getRubrics()) {
                // Initialize rubric ID to ensure it's loaded
                rubric.getId();
                rubric.getName();

                // Force load direct ratings on rubric
                if (rubric.getRatings() != null) {
                    rubric.getRatings().size(); // Initialize collection
                    // Touch each rating to ensure full load
                    for (Rating rating : rubric.getRatings()) {
                        rating.getId();
                        rating.getName();
                        rating.getMarks();
                    }
                }

                // Force load sub-rubrics
                if (rubric.getSubRubrics() != null) {
                    rubric.getSubRubrics().size(); // Initialize collection
                    for (SubRubric subRubric : rubric.getSubRubrics()) {
                        subRubric.getId();
                        subRubric.getName();
                        subRubric.getDescription();
                        subRubric.getMarks();

                        // Force load sub-rubric ratings - THIS IS CRITICAL
                        if (subRubric.getRatings() != null) {
                            subRubric.getRatings().size(); // Initialize collection
                            for (Rating rating : subRubric.getRatings()) {
                                rating.getId();
                                rating.getName();
                                rating.getDescription();
                                rating.getMarks();
                            }
                        }
                    }
                }
            }
        }

        return Optional.of(assessment);
    }

   @GetMapping("/home")
public String industrialSupervisorHome(Model model, Principal principal) {

    // Get current supervisor
    String username = principal.getName();
    Optional<IndustrialSupervisor> supervisorOpt = industrialSupervisorService.findByUsername(username);

    if (supervisorOpt.isEmpty()) {
        model.addAttribute("error", "Industrial supervisor not found");
        return "error";
    }

    IndustrialSupervisor supervisor = supervisorOpt.get();

    // Get assigned groups
    List<Group> assignedGroups = industrialSupervisorService.getAssignedGroups(supervisor.getId());
    model.addAttribute("assignedGroups", assignedGroups);
    model.addAttribute("supervisor", supervisor);

    // --- DEFINE UTILITY FUNCTIONS ---
    Function<Object, Boolean> isRubricType = component -> component instanceof Rubric;

    // Group by Assessment Type only
    Function<Assessment, Map<String, Map<String, List<Object>>>> groupAssessmentComponents = assessment -> {

        Map<String, Map<String, List<Object>>> finalGroup = new LinkedHashMap<>();
        final String DUMMY_EVAL_TYPE_KEY = "ASSESSMENT_GROUPING";

        // 1. Get all Rubric components
        Stream<Object> combinedComponents = Stream.empty();
        if (assessment.getRubrics() != null) {
            combinedComponents = assessment.getRubrics().stream().map(r -> (Object) r);
        }

        List<Object> components = combinedComponents.collect(Collectors.toList());

        // 2. Group components by Assessment Type (inner map)
        Map<String, List<Object>> byAssessType = components.stream()
                .collect(Collectors.groupingBy(c -> {
                    if (c instanceof Rubric rubric) {
                        return rubric.getAssessmentTypes();
                    }
                    return "Unknown";
                }, LinkedHashMap::new, Collectors.toList()));

        // 3. Wrap in an outer map with a dummy key
        finalGroup.put(DUMMY_EVAL_TYPE_KEY, byAssessType);

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

    return "industrial_supervisor_home";
}
    /**
     * Show list of assigned groups for evaluation
     */
    @GetMapping("/evaluate")
    public String showEvaluateGroups(Model model, Principal principal) {
        String username = principal.getName();
        Optional<IndustrialSupervisor> supervisorOpt = industrialSupervisorService.findByUsername(username);

        if (supervisorOpt.isEmpty()) {
            model.addAttribute("error", "Industrial supervisor not found");
            return "error";
        }

        IndustrialSupervisor supervisor = supervisorOpt.get();

        // Get the Industrial Supervisor Assessment
        Optional<Assessment> assessmentOpt = loadIndustrialSupervisorAssessment();
        if (assessmentOpt.isEmpty()) {
            model.addAttribute("error", "Industrial Supervisor Assessment not found. Please contact administrator.");
            return "error";
        }

        Assessment assessment = assessmentOpt.get();

        // Check deadline
        List<Deadline> supervisorDeadlines = deadlineService.getDeadlinesByAssessmentIdAndAssessorType(
                assessment.getId(), "SUPERVISOR");

        boolean isOpen = false;
        String deadlineMessage = "";
        Date now = new Date();

        if (!supervisorDeadlines.isEmpty()) {
            Deadline deadline = supervisorDeadlines.get(0);
            Date openDate = deadline.getOpenDate();
            Date closeDate = deadline.getDate();

            if (openDate != null && closeDate != null) {
                if (now.before(openDate)) {
                    deadlineMessage = "Assessment not yet open. Opens on: " + openDate;
                } else if (now.after(closeDate)) {
                    deadlineMessage = "Assessment deadline has passed. Closed on: " + closeDate;
                } else {
                    isOpen = true;
                }
            } else if (closeDate != null) {
                if (now.after(closeDate)) {
                    deadlineMessage = "Assessment deadline has passed. Closed on: " + closeDate;
                } else {
                    isOpen = true;
                }
            }
        } else {
            // No deadline set, allow evaluation
            isOpen = true;
        }

        if (!isOpen) {
            model.addAttribute("error", deadlineMessage);
            return "error";
        }

        List<Group> assignedGroups = industrialSupervisorService.getAssignedGroups(supervisor.getId());

        model.addAttribute("assignedGroups", assignedGroups);
        model.addAttribute("supervisor", supervisor);
        model.addAttribute("assessment", assessment);

        return "supervisor_evaluate_groups";
    }

    @Transactional(readOnly = true)
    @GetMapping("/evaluate/group/{groupId}")
    public String evaluateGroup(
            @PathVariable Long groupId,
            @RequestParam(required = false) String requiresGroup,
            Model model,
            Principal principal) {

        String username = principal.getName();
        Optional<IndustrialSupervisor> supervisorOpt = industrialSupervisorService.findByUsername(username);

        if (supervisorOpt.isEmpty()) {
            model.addAttribute("error", "Industrial supervisor not found");
            return "error";
        }

        IndustrialSupervisor supervisor = supervisorOpt.get();

        // Verify this group is assigned to this supervisor
        List<Group> assignedGroups = industrialSupervisorService.getAssignedGroups(supervisor.getId());
        Optional<Group> groupOpt = assignedGroups.stream()
                .filter(g -> g.getId().equals(groupId))
                .findFirst();

        if (groupOpt.isEmpty()) {
            model.addAttribute("error", "You are not assigned to this group");
            return "error";
        }

        Group group = groupOpt.get();

        // Get the Industrial Supervisor Assessment
        Optional<Assessment> assessmentOpt = loadIndustrialSupervisorAssessment();
        if (assessmentOpt.isEmpty()) {
            model.addAttribute("error", "Industrial Supervisor Assessment not found");
            return "error";
        }

        Assessment assessment = assessmentOpt.get();

        // Separate rubrics into group and individual
        List<Rubric> groupRubrics = assessment.getRubrics().stream()
                .filter(r -> "Group Assessment".equals(r.getAssessmentTypes()))
                .collect(Collectors.toList());

        List<Rubric> individualRubrics = assessment.getRubrics().stream()
                .filter(r -> "Individual Assessment".equals(r.getAssessmentTypes()))
                .collect(Collectors.toList());

        // Check if group evaluation already exists
        boolean groupEvaluationExists = industrialSupervisorService.hasGroupEvaluation(
                supervisor.getId(), group.getId(), assessment.getId());

        // If there are group rubrics, show group evaluation form
        if (!groupRubrics.isEmpty()) {
            model.addAttribute("group", group);
            model.addAttribute("assessment", assessment);
            model.addAttribute("supervisor", supervisor);
            model.addAttribute("groupRubrics", groupRubrics);
            model.addAttribute("individualRubrics", individualRubrics);
            model.addAttribute("hasGroupRubrics", true);
            model.addAttribute("hasIndividualRubrics", !individualRubrics.isEmpty());
            model.addAttribute("groupEvaluationExists", groupEvaluationExists);
            // ✅ FIXED: Use getGroupCommentCount() for Group Assessment rubrics
            model.addAttribute("commentCount", assessment.getGroupCommentCount());
            model.addAttribute("requiresGroupEvaluation", "true".equals(requiresGroup));

            // Load existing ratings if re-evaluating
            if (groupEvaluationExists) {
                Map<Long, Long> existingRatings = industrialSupervisorService.getGroupEvaluationRatings(
                        supervisor.getId(), group.getId(), assessment.getId());
                List<String> existingComments = industrialSupervisorService.getGroupEvaluationComments(
                        supervisor.getId(), group.getId(), assessment.getId());
                model.addAttribute("existingRatings", existingRatings);
                model.addAttribute("existingComments", existingComments);
            }
            return "supervisor_group_evaluation";
        }

        // No group rubrics, go straight to student selection
        // Need to add all required model attributes for student selection page
        List<Student> students = group.getStudents();
        Map<Long, Boolean> studentEvaluationStatus = industrialSupervisorService.getStudentEvaluationStatus(
                supervisor.getId(), group.getId(), assessment.getId(), students);

        model.addAttribute("group", group);
        model.addAttribute("assessment", assessment);
        model.addAttribute("students", students);
        model.addAttribute("supervisor", supervisor);
        model.addAttribute("studentEvaluationStatus", studentEvaluationStatus);

        return "supervisor_select_student";
    }

    /**
     * Submit group evaluation
     */
    @PostMapping("/evaluate/group/{groupId}/submit")
    public String submitGroupEvaluation(
            @PathVariable Long groupId,
            @RequestParam Map<String, String> allParams,
            Principal principal,
            Model model) {

        String username = principal.getName();
        Optional<IndustrialSupervisor> supervisorOpt = industrialSupervisorService.findByUsername(username);

        if (supervisorOpt.isEmpty()) {
            model.addAttribute("error", "Industrial supervisor not found");
            return "error";
        }

        IndustrialSupervisor supervisor = supervisorOpt.get();

        // Verify group assignment
        List<Group> assignedGroups = industrialSupervisorService.getAssignedGroups(supervisor.getId());
        Optional<Group> groupOpt = assignedGroups.stream()
                .filter(g -> g.getId().equals(groupId))
                .findFirst();

        if (groupOpt.isEmpty()) {
            model.addAttribute("error", "You are not assigned to this group");
            return "error";
        }

        Group group = groupOpt.get();

        // Get assessment
        Optional<Assessment> assessmentOpt = loadIndustrialSupervisorAssessment();
        if (assessmentOpt.isEmpty()) {
            model.addAttribute("error", "Assessment not found");
            return "error";
        }

        Assessment assessment = assessmentOpt.get();

        try {
            // Save group evaluation
            industrialSupervisorService.saveGroupEvaluation(
                    supervisor, group, assessment, allParams);

            // Check if there are individual rubrics
            List<Rubric> individualRubrics = assessment.getRubrics().stream()
                    .filter(r -> "Individual Assessment".equals(r.getAssessmentTypes()))
                    .collect(Collectors.toList());

            if (!individualRubrics.isEmpty()) {
                // Redirect to student selection
                return "redirect:/supervisor/evaluate/group/" + groupId + "/students";
            } else {
                // No individual rubrics, done!
                model.addAttribute("success", "Evaluation submitted successfully!");
                model.addAttribute("groupName", group.getGroupName());
                return "supervisor_evaluation_success";
            }

        } catch (Exception e) {
            model.addAttribute("error", "Failed to submit evaluation: " + e.getMessage());
            return "error";
        }
    }

    @GetMapping("/evaluate/group/{groupId}/students")
    public String selectStudent(
            @PathVariable Long groupId,
            @RequestParam(required = false) String skip,
            Model model,
            Principal principal) {

        String username = principal.getName();
        Optional<IndustrialSupervisor> supervisorOpt = industrialSupervisorService.findByUsername(username);

        if (supervisorOpt.isEmpty()) {
            model.addAttribute("error", "Industrial supervisor not found");
            return "error";
        }

        IndustrialSupervisor supervisor = supervisorOpt.get();

        // Verify group assignment
        List<Group> assignedGroups = industrialSupervisorService.getAssignedGroups(supervisor.getId());
        Optional<Group> groupOpt = assignedGroups.stream()
                .filter(g -> g.getId().equals(groupId))
                .findFirst();

        if (groupOpt.isEmpty()) {
            model.addAttribute("error", "You are not assigned to this group");
            return "error";
        }

        Group group = groupOpt.get();

        // Get assessment
        Optional<Assessment> assessmentOpt = loadIndustrialSupervisorAssessment();
        if (assessmentOpt.isEmpty()) {
            model.addAttribute("error", "Assessment not found");
            return "error";
        }

        Assessment assessment = assessmentOpt.get();

        // Check if there are group rubrics
        List<Rubric> groupRubrics = assessment.getRubrics().stream()
                .filter(r -> "Group Assessment".equals(r.getAssessmentTypes()))
                .collect(Collectors.toList());

        // If there are group rubrics and group evaluation hasn't been done yet,
        // redirect back
        // UNLESS they explicitly skipped
        if (!groupRubrics.isEmpty() && !"true".equals(skip)) {
            boolean groupEvaluationExists = industrialSupervisorService.hasGroupEvaluation(
                    supervisor.getId(), group.getId(), assessment.getId());

            if (!groupEvaluationExists) {
                // Redirect back to group evaluation with message
                return "redirect:/supervisor/evaluate/group/" + groupId + "?requiresGroup=true";
            }
        }

        List<Student> students = group.getStudents();

        // Check which students have been evaluated
        Map<Long, Boolean> studentEvaluationStatus = industrialSupervisorService.getStudentEvaluationStatus(
                supervisor.getId(), group.getId(), assessment.getId(), students);

        model.addAttribute("group", group);
        model.addAttribute("assessment", assessment);
        model.addAttribute("students", students);
        model.addAttribute("supervisor", supervisor);
        model.addAttribute("studentEvaluationStatus", studentEvaluationStatus);

        return "supervisor_select_student";
    }

    /**
     * Evaluate individual student
     */
    @Transactional(readOnly = true)
    @GetMapping("/evaluate/group/{groupId}/student/{studentId}")
    public String evaluateStudent(
            @PathVariable Long groupId,
            @PathVariable Long studentId,
            Model model,
            Principal principal) {

        String username = principal.getName();
        Optional<IndustrialSupervisor> supervisorOpt = industrialSupervisorService.findByUsername(username);

        if (supervisorOpt.isEmpty()) {
            model.addAttribute("error", "Industrial supervisor not found");
            return "error";
        }

        IndustrialSupervisor supervisor = supervisorOpt.get();

        // Verify group and student
        List<Group> assignedGroups = industrialSupervisorService.getAssignedGroups(supervisor.getId());
        Optional<Group> groupOpt = assignedGroups.stream()
                .filter(g -> g.getId().equals(groupId))
                .findFirst();

        if (groupOpt.isEmpty()) {
            model.addAttribute("error", "You are not assigned to this group");
            return "error";
        }

        Group group = groupOpt.get();

        Optional<Student> studentOpt = group.getStudents().stream()
                .filter(s -> s.getId().equals(studentId))
                .findFirst();

        if (studentOpt.isEmpty()) {
            model.addAttribute("error", "Student not found in this group");
            return "error";
        }

        Student student = studentOpt.get();

        // Get assessment
        Optional<Assessment> assessmentOpt = loadIndustrialSupervisorAssessment();
        if (assessmentOpt.isEmpty()) {
            model.addAttribute("error", "Assessment not found");
            return "error";
        }

        Assessment assessment = assessmentOpt.get();

        // Get only individual rubrics
        List<Rubric> individualRubrics = assessment.getRubrics().stream()
                .filter(r -> "Individual Assessment".equals(r.getAssessmentTypes()))
                .collect(Collectors.toList());

        // Check if student evaluation exists (for re-evaluation warning)
        boolean evaluationExists = industrialSupervisorService.hasStudentEvaluation(
                supervisor.getId(), student.getId(), assessment.getId());

        model.addAttribute("group", group);
        model.addAttribute("student", student);
        model.addAttribute("assessment", assessment);
        model.addAttribute("supervisor", supervisor);
        model.addAttribute("individualRubrics", individualRubrics);
        model.addAttribute("evaluationExists", evaluationExists);
        // ✅ FIXED: Use getIndividualCommentCount() for Individual Assessment rubrics
        model.addAttribute("commentCount", assessment.getIndividualCommentCount());

        // Load existing ratings if re-evaluating
        if (evaluationExists) {
            Map<Long, Long> existingRatings = industrialSupervisorService.getStudentEvaluationRatings(
                    supervisor.getId(), student.getId(), assessment.getId());
            List<String> existingComments = industrialSupervisorService.getStudentEvaluationComments(
                    supervisor.getId(), student.getId(), assessment.getId());
            model.addAttribute("existingRatings", existingRatings);
            model.addAttribute("existingComments", existingComments);
        }

        return "supervisor_student_evaluation";
    }

    /**
     * Submit individual student evaluation
     */
    @PostMapping("/evaluate/group/{groupId}/student/{studentId}/submit")
    public String submitStudentEvaluation(
            @PathVariable Long groupId,
            @PathVariable Long studentId,
            @RequestParam Map<String, String> allParams,
            Principal principal,
            Model model) {

        String username = principal.getName();
        Optional<IndustrialSupervisor> supervisorOpt = industrialSupervisorService.findByUsername(username);

        if (supervisorOpt.isEmpty()) {
            model.addAttribute("error", "Industrial supervisor not found");
            return "error";
        }

        IndustrialSupervisor supervisor = supervisorOpt.get();

        // Verify group and student
        List<Group> assignedGroups = industrialSupervisorService.getAssignedGroups(supervisor.getId());
        Optional<Group> groupOpt = assignedGroups.stream()
                .filter(g -> g.getId().equals(groupId))
                .findFirst();

        if (groupOpt.isEmpty()) {
            model.addAttribute("error", "You are not assigned to this group");
            return "error";
        }

        Group group = groupOpt.get();

        Optional<Student> studentOpt = group.getStudents().stream()
                .filter(s -> s.getId().equals(studentId))
                .findFirst();

        if (studentOpt.isEmpty()) {
            model.addAttribute("error", "Student not found in this group");
            return "error";
        }

        Student student = studentOpt.get();

        // Get assessment
        Optional<Assessment> assessmentOpt = loadIndustrialSupervisorAssessment();
        if (assessmentOpt.isEmpty()) {
            model.addAttribute("error", "Assessment not found");
            return "error";
        }

        Assessment assessment = assessmentOpt.get();

        try {
            // Save student evaluation
            industrialSupervisorService.saveStudentEvaluation(
                    supervisor, student, assessment, allParams);

            // Redirect back to student selection
            return "redirect:/supervisor/evaluate/group/" + groupId + "/students";

        } catch (Exception e) {
            model.addAttribute("error", "Failed to submit evaluation: " + e.getMessage());
            return "error";
        }
    }
}