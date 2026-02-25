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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Deadline;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.IndustrialSupervisor;
import com.capstone.adproject.model.LecturerGroupAssignment;
import com.capstone.adproject.model.Rating;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.SubRubric;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.DeadlineService;
import com.capstone.adproject.service.IndustrialSupervisorService;

@Controller
@RequestMapping("/supervisor")
public class IndustrialSupervisorController {

    private final AssessmentService assessmentService;
    private final DeadlineService deadlineService;
    private final IndustrialSupervisorService industrialSupervisorService;
    private final LecturerGroupAssignmentRepository lecturerGroupAssignmentRepository;

    public IndustrialSupervisorController(
            AssessmentService assessmentService,
            DeadlineService deadlineService,
            IndustrialSupervisorService industrialSupervisorService,
            LecturerGroupAssignmentRepository lecturerGroupAssignmentRepository) {
        this.assessmentService = assessmentService;
        this.deadlineService = deadlineService;
        this.industrialSupervisorService = industrialSupervisorService;
        this.lecturerGroupAssignmentRepository = lecturerGroupAssignmentRepository;
    }

    @Transactional(readOnly = true)
    private Optional<Assessment> getAssessmentForGroup(Group group) {
        Assessment assessment = null;

        List<LecturerGroupAssignment> assignments = lecturerGroupAssignmentRepository.findByGroup(group);
        
        if (!assignments.isEmpty()) {
            Optional<LecturerGroupAssignment> supervisorAssignment = assignments.stream()
                .filter(a -> a.getAssessment() != null)
                .filter(a -> {
                    List<Deadline> deadlines = deadlineService.getDeadlinesByAssessmentIdAndAssessorType(
                        a.getAssessment().getId(), "SUPERVISOR");
                    return !deadlines.isEmpty();
                })
                .findFirst();
            
            if (supervisorAssignment.isPresent()) {
                assessment = supervisorAssignment.get().getAssessment();
            }
        } 

        if (assessment == null) {
            List<Assessment> allAssessments = assessmentService.findAllAssessmentsWithRubrics();
            Optional<Assessment> fallbackOpt = allAssessments.stream()
                .filter(a -> a.getTitle() != null && 
                            a.getTitle().toLowerCase().contains("supervisor"))
                .filter(a -> {
                    List<Deadline> deadlines = deadlineService.getDeadlinesByAssessmentIdAndAssessorType(
                        a.getId(), "SUPERVISOR");
                    return !deadlines.isEmpty();
                })
                .findFirst();
            
            if (fallbackOpt.isPresent()) {
                assessment = fallbackOpt.get();
            }
        }

        if (assessment == null) {
            return Optional.empty();
        }

        initializeAssessmentCollections(assessment);

        return Optional.of(assessment);
    }

    private void initializeAssessmentCollections(Assessment assessment) {
        if (assessment.getRubrics() != null) {
            for (Rubric rubric : assessment.getRubrics()) {
                rubric.getId();
                rubric.getName();

                if (rubric.getRatings() != null) {
                    rubric.getRatings().size();
                    for (Rating rating : rubric.getRatings()) {
                        rating.getId();
                        rating.getName();
                        rating.getMarks();
                    }
                }

                if (rubric.getSubRubrics() != null) {
                    rubric.getSubRubrics().size();
                    for (SubRubric subRubric : rubric.getSubRubrics()) {
                        subRubric.getId();
                        subRubric.getName();
                        subRubric.getDescription();
                        subRubric.getMarks();

                        if (subRubric.getRatings() != null) {
                            subRubric.getRatings().size();
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
    }

    @GetMapping("/home")
    public String industrialSupervisorHome(Model model, Principal principal) {
        String emailOrUsername = principal.getName(); 
        Optional<IndustrialSupervisor> supervisorOpt = 
            industrialSupervisorService.findByEmail(emailOrUsername)
                .or(() -> industrialSupervisorService.findByUsername(emailOrUsername));

        if (supervisorOpt.isEmpty()) {
            model.addAttribute("error", "Industrial supervisor not found");
            return "error";
        }

        IndustrialSupervisor supervisor = supervisorOpt.get();

        List<Group> assignedGroups = industrialSupervisorService.getAssignedGroups(supervisor.getId());
        model.addAttribute("assignedGroups", assignedGroups);
        model.addAttribute("supervisor", supervisor);

        Function<Object, Boolean> isRubricType = component -> component instanceof Rubric;

        Function<Assessment, Map<String, Map<String, List<Object>>>> groupAssessmentComponents = assessment -> {
            Map<String, Map<String, List<Object>>> finalGroup = new LinkedHashMap<>();
            final String DUMMY_EVAL_TYPE_KEY = "ASSESSMENT_GROUPING";

            Stream<Object> combinedComponents = Stream.empty();
            if (assessment.getRubrics() != null) {
                combinedComponents = assessment.getRubrics().stream().map(r -> (Object) r);
            }

            List<Object> components = combinedComponents.collect(Collectors.toList());

            Map<String, List<Object>> byAssessType = components.stream()
                    .collect(Collectors.groupingBy(c -> {
                        if (c instanceof Rubric rubric) {
                            return rubric.getAssessmentTypes();
                        }
                        return "Unknown";
                    }, LinkedHashMap::new, Collectors.toList()));

            finalGroup.put(DUMMY_EVAL_TYPE_KEY, byAssessType);

            return finalGroup;
        };

        List<Assessment> allAssessments = assessmentService.findAllAssessmentsWithRubrics();
        model.addAttribute("allAssessments", allAssessments);

        List<Deadline> allDeadlines = deadlineService.getAllDeadlines();
        model.addAttribute("allDeadlines", allDeadlines);

        model.addAttribute("groupAssessmentComponents", groupAssessmentComponents);
        model.addAttribute("isRubricType", isRubricType);

        return "industrial_supervisor_home";
    }

    @GetMapping("/evaluate")
    public String showEvaluateGroups(Model model, Principal principal) {
        String emailOrUsername = principal.getName(); // ✅ FIXED: Declare variable
        Optional<IndustrialSupervisor> supervisorOpt = 
            industrialSupervisorService.findByEmail(emailOrUsername)
                .or(() -> industrialSupervisorService.findByUsername(emailOrUsername));

        if (supervisorOpt.isEmpty()) {
            model.addAttribute("error", "Industrial supervisor not found");
            return "error";
        }

        IndustrialSupervisor supervisor = supervisorOpt.get();
        List<Group> assignedGroups = industrialSupervisorService.getAssignedGroups(supervisor.getId());
        Map<Long, Map<String, Object>> groupProgress = new LinkedHashMap<>();

        for (Group group : assignedGroups) {
            Optional<Assessment> assessmentOpt = getAssessmentForGroup(group);
            
            if (assessmentOpt.isPresent()) {
                Assessment assessment = assessmentOpt.get();
                Map<String, Object> progress = industrialSupervisorService.getEvaluationProgress(
                        supervisor.getId(), group, assessment);
                groupProgress.put(group.getId(), progress);

            } else {
                 Map<String, Object> errorProgress = new LinkedHashMap<>();
                 errorProgress.put("status", "NO_ASSESSMENT");
                 errorProgress.put("percentage", 0.0);
                 groupProgress.put(group.getId(), errorProgress);
            }
        }

        model.addAttribute("assignedGroups", assignedGroups);
        model.addAttribute("supervisor", supervisor);
        model.addAttribute("groupProgress", groupProgress);

        model.addAttribute("assessmentOpen", true); 

        return "supervisor_evaluate_groups";
    }

    @Transactional(readOnly = true)
    @GetMapping("/evaluate/group/{groupId}")
    public String showContinuousEvaluation(
            @PathVariable Long groupId,
            Model model,
            Principal principal) {

        String emailOrUsername = principal.getName();
        Optional<IndustrialSupervisor> supervisorOpt = 
            industrialSupervisorService.findByEmail(emailOrUsername)
                .or(() -> industrialSupervisorService.findByUsername(emailOrUsername));

        if (supervisorOpt.isEmpty()) {
            model.addAttribute("error", "Industrial supervisor not found");
            return "error";
        }

        IndustrialSupervisor supervisor = supervisorOpt.get();

        List<Group> assignedGroups = industrialSupervisorService.getAssignedGroups(supervisor.getId());
        Optional<Group> groupOpt = assignedGroups.stream()
                .filter(g -> g.getId().equals(groupId))
                .findFirst();

        if (groupOpt.isEmpty()) {
            model.addAttribute("error", "You are not assigned to this group");
            return "error";
        }

        Group group = groupOpt.get();

        Optional<Assessment> assessmentOpt = getAssessmentForGroup(group);
        if (assessmentOpt.isEmpty()) {
            model.addAttribute("error", "No assessment found assigned to this group.");
            return "error";
        }

        Assessment assessment = assessmentOpt.get();

        List<Deadline> supervisorDeadlines = deadlineService.getDeadlinesByAssessmentIdAndAssessorType(
                assessment.getId(), "SUPERVISOR");

        boolean isOpen = true;
        String deadlineMessage = "";
        Date now = new Date();

        if (!supervisorDeadlines.isEmpty()) {
            Deadline deadline = supervisorDeadlines.get(0);
            Date openDate = deadline.getOpenDate();
            Date closeDate = deadline.getDate();

            if (openDate != null && now.before(openDate)) {
                deadlineMessage = "Assessment not yet open. Opens on: " + openDate;
                isOpen = false;
            } else if (closeDate != null && now.after(closeDate)) {
                deadlineMessage = "Assessment deadline has passed. Closed on: " + closeDate;
                isOpen = false;
            }
        }

        if (!isOpen) {
            model.addAttribute("error", deadlineMessage);
            return "error";
        }

        Map<String, Object> progress = industrialSupervisorService.getEvaluationProgress(
            supervisor.getId(), group, assessment);

        List<Rubric> groupRubrics = assessment.getRubrics().stream()
                .filter(r -> "Group Assessment".equals(r.getAssessmentTypes()))
                .collect(Collectors.toList());

        List<Rubric> individualRubrics = assessment.getRubrics().stream()
                .filter(r -> "Individual Assessment".equals(r.getAssessmentTypes()))
                .collect(Collectors.toList());

        Map<String, Object> existingData = 
            industrialSupervisorService.loadExistingEvaluation(supervisor, group, assessment);

        model.addAttribute("group", group);
        model.addAttribute("assessment", assessment);
        model.addAttribute("supervisor", supervisor);
        model.addAttribute("students", group.getStudents());
        model.addAttribute("groupRubrics", groupRubrics);
        model.addAttribute("individualRubrics", individualRubrics);
        model.addAttribute("progress", progress);
        model.addAttribute("existingData", existingData);
        model.addAttribute("groupCommentCount", assessment.getGroupCommentCount());
        model.addAttribute("individualCommentCount", assessment.getIndividualCommentCount());
        model.addAttribute("isReEvaluation", !"NOT_STARTED".equals(progress.get("status")));

        return "supervisor_continuous_evaluation";
    }

    @PostMapping("/evaluate/group/{groupId}/save")
    public String saveContinuousEvaluation(
            @PathVariable Long groupId,
            @RequestParam(required = false) String action,
            @RequestParam Map<String, String> allParams,
            Principal principal,
            Model model,
            RedirectAttributes redirectAttributes) {

        String emailOrUsername = principal.getName();
        Optional<IndustrialSupervisor> supervisorOpt = 
            industrialSupervisorService.findByEmail(emailOrUsername)
                .or(() -> industrialSupervisorService.findByUsername(emailOrUsername));

        if (supervisorOpt.isEmpty()) {
            model.addAttribute("error", "Industrial supervisor not found");
            return "error";
        }

        IndustrialSupervisor supervisor = supervisorOpt.get();

        List<Group> assignedGroups = industrialSupervisorService.getAssignedGroups(supervisor.getId());
        Optional<Group> groupOpt = assignedGroups.stream()
                .filter(g -> g.getId().equals(groupId))
                .findFirst();

        if (groupOpt.isEmpty()) {
            model.addAttribute("error", "You are not assigned to this group");
            return "error";
        }

        Group group = groupOpt.get();

        Optional<Assessment> assessmentOpt = getAssessmentForGroup(group);
        if (assessmentOpt.isEmpty()) {
            model.addAttribute("error", "Assessment not found");
            return "error";
        }

        Assessment assessment = assessmentOpt.get();

        try {
            industrialSupervisorService.saveContinuousEvaluation(
                supervisor, group, assessment, allParams);

            Map<String, Object> progress = industrialSupervisorService.getEvaluationProgress(
                supervisor.getId(), group, assessment);

            String status = (String) progress.get("status");

            if ("COMPLETED".equals(status)) {
                redirectAttributes.addFlashAttribute("successMessage",
                    "Evaluation completed successfully for " + group.getGroupName() + "!");
            } else {
                redirectAttributes.addFlashAttribute("successMessage",
                    "Progress saved successfully. You can continue later.");
            }

            return "redirect:/supervisor/evaluate";

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("errorMessage",
                "Failed to save evaluation: " + e.getMessage());
            return "redirect:/supervisor/evaluate/group/" + groupId;
        }
    }
}