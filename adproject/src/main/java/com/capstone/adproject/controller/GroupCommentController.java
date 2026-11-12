package com.capstone.adproject.controller;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List; // Added this import
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.capstone.adproject.dto.ai.AiTeamDynamicsDtos;
import com.capstone.adproject.model.AdminCourseAssignment;
import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Mark;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.repositories.AdminCourseAssignmentRepository;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.LecturerRubricAssignmentRepository;
import com.capstone.adproject.repositories.MarkRepository;
import com.capstone.adproject.service.AdminService;
import com.capstone.adproject.service.AiServiceClient;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.CalculateService;
import com.capstone.adproject.service.CourseScopeService;

@Controller
@RequestMapping("/{role}/comments/view")
public class GroupCommentController {

    private static final Logger logger = LoggerFactory.getLogger(GroupCommentController.class);

    private final AssessmentService assessmentService;
    private final GroupRepository groupRepository;
    private final LecturerRepository lecturerRepository;
    private final LecturerGroupAssignmentRepository lecturerGroupAssignmentRepository;
    private final LecturerRubricAssignmentRepository lecturerRubricAssignmentRepository;
    private final AdminCourseAssignmentRepository adminCourseAssignmentRepository;
    private final CalculateService calculateService;
    private final CourseScopeService courseScopeService;
    private final MarkRepository markRepository;
    private final AiServiceClient aiServiceClient;

    @Autowired(required = false)
    private AdminService adminService;

    public GroupCommentController(
            AssessmentService assessmentService,
            GroupRepository groupRepository,
            LecturerRepository lecturerRepository,
            LecturerGroupAssignmentRepository lecturerGroupAssignmentRepository,
            LecturerRubricAssignmentRepository lecturerRubricAssignmentRepository,
            AdminCourseAssignmentRepository adminCourseAssignmentRepository,
            CalculateService calculateService,
            CourseScopeService courseScopeService) {
        this(assessmentService, groupRepository, lecturerRepository, lecturerGroupAssignmentRepository,
             lecturerRubricAssignmentRepository, adminCourseAssignmentRepository, calculateService,
             courseScopeService, null, null);
    }

    @Autowired
    public GroupCommentController(
            AssessmentService assessmentService,
            GroupRepository groupRepository,
            LecturerRepository lecturerRepository,
            LecturerGroupAssignmentRepository lecturerGroupAssignmentRepository,
            LecturerRubricAssignmentRepository lecturerRubricAssignmentRepository,
            AdminCourseAssignmentRepository adminCourseAssignmentRepository,
            CalculateService calculateService,
            CourseScopeService courseScopeService,
            @Autowired(required = false) MarkRepository markRepository,
            @Autowired(required = false) AiServiceClient aiServiceClient) {
        this.assessmentService = assessmentService;
        this.groupRepository = groupRepository;
        this.lecturerRepository = lecturerRepository;
        this.lecturerGroupAssignmentRepository = lecturerGroupAssignmentRepository;
        this.lecturerRubricAssignmentRepository = lecturerRubricAssignmentRepository;
        this.adminCourseAssignmentRepository = adminCourseAssignmentRepository;
        this.calculateService = calculateService;
        this.courseScopeService = courseScopeService;
        this.markRepository = markRepository;
        this.aiServiceClient = aiServiceClient;
    }

    private Long getResolvedActiveCourseId() {
        Long activeCourseId = courseScopeService.getActiveCourseIdForCurrentUser();
        if (activeCourseId != null && activeCourseId > 0) {
            return activeCourseId;
        }
        Set<Long> activeCourseIds = courseScopeService.getActiveCourseIdsForCurrentUser();
        if (activeCourseIds != null && !activeCourseIds.isEmpty()) {
            return activeCourseIds.iterator().next();
        }
        return null;
    }

    private Optional<Lecturer> findLecturerByAuthName(String authName) {
        Long activeCourseId = getResolvedActiveCourseId();
        return CourseScopeService.resolveLecturerForCourse(lecturerRepository, authName, activeCourseId);
    }

    private List<Student> resolveStudentsForGroup(Group group) {
        if (group == null) {
            return List.of();
        }
        if (adminService != null) {
            List<Student> scoped = adminService.getStudentsByGroup(group);
            if (scoped != null) {
                return new java.util.ArrayList<>(scoped);
            }
        }
        return group.getStudents() != null ? new java.util.ArrayList<>(group.getStudents()) : List.of();
    }

    private List<Assessment> findAssignedAssessmentsForLecturer(Lecturer lecturer) {
        if (lecturer == null || lecturer.getId() == null) {
            return List.of();
        }
        Long targetCourseId = getResolvedActiveCourseId();
        if (targetCourseId == null && lecturer.getCourse() != null) {
            targetCourseId = lecturer.getCourse().getId();
        }
        final Long scopedCourseId = targetCourseId;

        Map<Long, Assessment> assignedAssessmentsById = new LinkedHashMap<>();

        lecturerGroupAssignmentRepository.findAssessmentsByLecturer(lecturer).forEach(assessment -> {
            if (assessment != null && assessment.getId() != null
                    && (scopedCourseId == null || (assessment.getCourse() != null && scopedCourseId.equals(assessment.getCourse().getId())))) {
                assignedAssessmentsById.putIfAbsent(assessment.getId(), assessment);
            }
        });

        lecturerRubricAssignmentRepository.findAssessmentsByLecturer(lecturer).forEach(assessment -> {
            if (assessment != null && assessment.getId() != null
                    && (scopedCourseId == null || (assessment.getCourse() != null && scopedCourseId.equals(assessment.getCourse().getId())))) {
                assignedAssessmentsById.putIfAbsent(assessment.getId(), assessment);
            }
        });

        // Add assessments for the lecturer's active/assigned course where they supervise a group
        if (scopedCourseId != null) {
            boolean supervisesInCourse = groupRepository.findByCourseId(scopedCourseId).stream()
                .anyMatch(g -> (g.getAcademicSupervisor() != null && g.getAcademicSupervisor().getId().equals(lecturer.getId())) ||
                               (g.getIndustrialSupervisor() != null && g.getIndustrialSupervisor().getId().equals(lecturer.getId())));

            if (supervisesInCourse) {
                assessmentService.findAllAssessmentsWithRubricsByCourseId(scopedCourseId).forEach(assessment -> {
                    if (assessment != null && assessment.getId() != null) {
                        assignedAssessmentsById.putIfAbsent(assessment.getId(), assessment);
                    }
                });
            }
        }

        return assignedAssessmentsById.values().stream().collect(Collectors.toList());
    }

    private boolean isLecturerAssignedToAssessment(Lecturer lecturer, Assessment assessment) {
        if (lecturer == null || assessment == null || assessment.getCourse() == null || assessment.getCourse().getId() == null) {
            return false;
        }
        if (lecturer.getCourse() == null || lecturer.getCourse().getId() == null
                || !lecturer.getCourse().getId().equals(assessment.getCourse().getId())) {
            return false;
        }
        Long activeCourseId = getResolvedActiveCourseId();
        if (activeCourseId != null && !activeCourseId.equals(assessment.getCourse().getId())) {
            return false;
        }

        boolean hasGroupAssignment = !lecturerGroupAssignmentRepository
            .findByLecturerAndAssessment(lecturer, assessment)
            .isEmpty();
        boolean hasRubricAssignment = lecturerRubricAssignmentRepository
            .existsByLecturerAndAssessment(lecturer, assessment);
            
        boolean isSupervisor = groupRepository.findByCourseId(assessment.getCourse().getId()).stream()
            .anyMatch(g -> (g.getAcademicSupervisor() != null && g.getAcademicSupervisor().getId().equals(lecturer.getId())) ||
                           (g.getIndustrialSupervisor() != null && g.getIndustrialSupervisor().getId().equals(lecturer.getId())));

        return hasGroupAssignment || hasRubricAssignment || isSupervisor;
    }

    private Set<Long> getManagedCourseIdsForAdmin(String adminEmail) {
        Set<Long> activeCourseIds = courseScopeService.getActiveCourseIdsForCurrentUser();
        if (activeCourseIds != null && !activeCourseIds.isEmpty()) {
            return activeCourseIds;
        }
        Long activeCourseId = courseScopeService.getActiveCourseIdForCurrentUser();
        if (activeCourseId != null && activeCourseId > 0) {
            return Set.of(activeCourseId);
        }
        return Set.of();
    }

    private boolean assessmentInManagedCourses(Assessment assessment, Set<Long> managedCourseIds) {
        return assessment != null
            && assessment.getCourse() != null
            && assessment.getCourse().getId() != null
            && managedCourseIds.contains(assessment.getCourse().getId());
    }

    private boolean groupInManagedCourses(Group group, Set<Long> managedCourseIds) {
        return group != null
            && group.getCourse() != null
            && group.getCourse().getId() != null
            && managedCourseIds.contains(group.getCourse().getId());
    }

    @GetMapping("/assessments")
    public String selectAssessment(@PathVariable("role") String role, Authentication auth, Model model) {
        boolean isAdmin = "admin".equalsIgnoreCase(role);
        logger.info("selectAssessment: Accessed with role='{}', isAdmin={}", role, isAdmin);
        if (isAdmin) {
            return "redirect:/admin/progress-tracking?tab=comments";
        } else {
            Optional<Lecturer> lecturerOpt = findLecturerByAuthName(auth.getName());
            if (lecturerOpt.isEmpty()) {
                model.addAttribute("assessments", List.of());
                model.addAttribute("errorMessage", "Assessor profile not found.");
            } else {
                model.addAttribute("assessments", findAssignedAssessmentsForLecturer(lecturerOpt.get()));
            }
        }
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("role", role);
        return "lecturer_comment_select_assessment";
    }

    @GetMapping("/assessment/{assessmentId}/groups")
    public String selectGroup(@PathVariable("role") String role, @PathVariable("assessmentId") Long assessmentId, Authentication auth, Model model, RedirectAttributes redirectAttributes) {
        boolean isAdmin = "admin".equalsIgnoreCase(role);
        logger.info("selectGroup: Accessed with role='{}', isAdmin={}", role, isAdmin); // Debug log
        Optional<Assessment> assessmentOpt = assessmentService.getAssessmentById(assessmentId);
        if (assessmentOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Assessment not found.");
            return "redirect:/" + role + "/comments/view/assessments";
        } // This method is now exclusively for Admin
        if (!"admin".equalsIgnoreCase(role)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Access Denied: Group selection is not available for this role.");
            return "redirect:/" + role + "/comments/view/assessments";
        } 

        List<Group> groups;
        Set<Long> managedCourseIds = getManagedCourseIdsForAdmin(auth.getName());

        if (!assessmentInManagedCourses(assessmentOpt.get(), managedCourseIds)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Assessment is outside your managed courses.");
            return "redirect:/" + role + "/comments/view/assessments";
        }

        if (isAdmin) {
            logger.info("selectGroup: Admin context. Fetching active-course groups.");
            Long assessmentCourseId = assessmentOpt.get().getCourse().getId();
            groups = groupRepository.findByCourseId(assessmentCourseId).stream()
                .filter(g -> groupInManagedCourses(g, managedCourseIds))
                .collect(Collectors.toList());
        } else {
            String username = auth.getName();
            Optional<Lecturer> lecturerOpt = findLecturerByAuthName(username);
            
            if (lecturerOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Assessor profile not found.");
                logger.warn("selectGroup: Lecturer profile not found for username: {}", username);
                return "redirect:/";
            }
            Lecturer lecturer = lecturerOpt.get();
            Long assessmentCourseId = assessmentOpt.get().getCourse().getId();

            // Filter groups where the current lecturer is either Academic or Industrial Supervisor within this course
            groups = groupRepository.findByCourseId(assessmentCourseId).stream()
                .filter(g -> (g.getAcademicSupervisor() != null && g.getAcademicSupervisor().getId().equals(lecturer.getId())) ||
                             (g.getIndustrialSupervisor() != null && g.getIndustrialSupervisor().getId().equals(lecturer.getId())))
                .collect(Collectors.toList());
        }

        model.addAttribute("assessment", assessmentOpt.get());
        model.addAttribute("groups", groups);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("role", role);
        return "comment_select_group";
    }

    @GetMapping("/assessment/{assessmentId}/group/{groupId}")
    public String viewComments(@PathVariable("role") String role, @PathVariable("assessmentId") Long assessmentId, 
                               @PathVariable("groupId") Long groupId, Authentication auth, Model model, RedirectAttributes redirectAttributes) {
        Optional<Assessment> assessmentOpt = assessmentService.getAssessmentById(assessmentId);
        boolean isAdmin = "admin".equalsIgnoreCase(role);
        logger.info("viewComments (Admin/Specific Group): Accessed with role='{}', isAdmin={}", role, isAdmin);
        Optional<Group> groupOpt = groupId == null ? Optional.empty() : groupRepository.findById(groupId);
        if (!isAdmin) { // This method is now exclusively for Admin
            return "redirect:/" + role + "/comments/view/assessment/" + assessmentId + "/view"; // Redirect to lecturer's combined view
        }
        
        if (assessmentOpt.isEmpty() || groupOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Assessment or Group not found.");
            return "redirect:/" + role + "/comments/view/assessments";
        }
        
        Assessment assessment = assessmentOpt.get();
        Group group = groupOpt.get();
        Set<Long> managedCourseIds = getManagedCourseIdsForAdmin(auth.getName());
        if (!assessmentInManagedCourses(assessment, managedCourseIds) || !groupInManagedCourses(group, managedCourseIds)
                || !assessment.getCourse().getId().equals(group.getCourse().getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Unauthorized access to another course's comments.");
            return "redirect:/" + role + "/comments/view/assessments";
        }
        List<Student> students = resolveStudentsForGroup(group);
        students.sort((s1, s2) -> {
            String name1 = (s1.getUsername() != null && !s1.getUsername().trim().isEmpty()) ? s1.getUsername() : (s1.getEmail() != null ? s1.getEmail() : "");
            String name2 = (s2.getUsername() != null && !s2.getUsername().trim().isEmpty()) ? s2.getUsername() : (s2.getEmail() != null ? s2.getEmail() : "");
            return name1.compareToIgnoreCase(name2);
        });
        
        // Fetch comments using existing CalculateService which properly identifies Evaluators (Peer/Lecturer)
        Map<Long, Object> allCommentsMap = new HashMap<>();

        for (Student student : students) {
            allCommentsMap.put(student.getId(), calculateService.getAllAssessmentComments(student, assessment));
        }

        model.addAttribute("assessment", assessment);
        model.addAttribute("group", group);
        model.addAttribute("students", students);
        model.addAttribute("allCommentsMap", allCommentsMap);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("role", role);
        return "comment_view";
    }

    @GetMapping("/assessment/{assessmentId}/view")
    public String viewLecturerComments(@PathVariable("role") String role, @PathVariable("assessmentId") Long assessmentId, 
                                       Authentication auth, Model model, RedirectAttributes redirectAttributes) {
        boolean isAdmin = "admin".equalsIgnoreCase(role);
        logger.info("viewLecturerComments (Combined View): Accessed with role='{}', isAdmin={}", role, isAdmin);

        if (isAdmin) { // This method is exclusively for Lecturers
            redirectAttributes.addFlashAttribute("errorMessage", "Access Denied: Admin should use the group selection flow.");
            return "redirect:/" + role + "/comments/view/assessments";
        }

        Optional<Assessment> assessmentOpt = assessmentService.getAssessmentById(assessmentId);
        if (assessmentOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Assessment not found.");
            return "redirect:/" + role + "/comments/view/assessments";
        }
        Assessment assessment = assessmentOpt.get();

        String username = auth.getName();
        Optional<Lecturer> lecturerOpt = findLecturerByAuthName(username);
        
        if (lecturerOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Assessor profile not found.");
            logger.warn("viewLecturerComments: Lecturer profile not found for username: {}", username);
            return "redirect:/";
        }
        Lecturer lecturer = lecturerOpt.get();
        
        if (assessment.getCourse() == null || lecturer.getCourse() == null || 
            !assessment.getCourse().getId().equals(lecturer.getCourse().getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to view comments for this assessment.");
            return "redirect:/" + role + "/comments/view/assessments";
        }

        if (!isLecturerAssignedToAssessment(lecturer, assessment)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You are not assigned to this assessment.");
            return "redirect:/" + role + "/comments/view/assessments";
        }

        // Filter groups where the current lecturer is either Academic or Industrial Supervisor within this assessment's course
        List<Group> supervisedGroups = groupRepository.findByCourseId(assessment.getCourse().getId()).stream()
            .filter(g -> (g.getAcademicSupervisor() != null && g.getAcademicSupervisor().getId().equals(lecturer.getId())) ||
                         (g.getIndustrialSupervisor() != null && g.getIndustrialSupervisor().getId().equals(lecturer.getId())))
            .collect(Collectors.toList());

        if (supervisedGroups.isEmpty()) {
            model.addAttribute("errorMessage", "You are not supervising any groups for this assessment.");
            model.addAttribute("assessment", assessment);
            model.addAttribute("role", role);
            return "lecturer_comment_view"; // Show empty view with message
        }

        // Prepare data structure for multiple groups and their students' comments
        Map<Group, Map<Student, Map<String, Object>>> commentsByGroupAndStudent = new LinkedHashMap<>();

        for (Group group : supervisedGroups) {
            Map<Student, Map<String, Object>> commentsByStudent = new LinkedHashMap<>();
            List<Student> sortedStudents = resolveStudentsForGroup(group);
            sortedStudents.sort((s1, s2) -> {
                String name1 = (s1.getUsername() != null && !s1.getUsername().trim().isEmpty()) ? s1.getUsername() : (s1.getEmail() != null ? s1.getEmail() : "");
                String name2 = (s2.getUsername() != null && !s2.getUsername().trim().isEmpty()) ? s2.getUsername() : (s2.getEmail() != null ? s2.getEmail() : "");
                return name1.compareToIgnoreCase(name2);
            });
            for (Student student : sortedStudents) {
                Map<String, Object> studentComments = new HashMap<>();
                studentComments.put("allComments", calculateService.getAllAssessmentComments(student, assessment));
                commentsByStudent.put(student, studentComments);
            }
            commentsByGroupAndStudent.put(group, commentsByStudent);
        }

        model.addAttribute("assessment", assessment);
        model.addAttribute("commentsByGroupAndStudent", commentsByGroupAndStudent);
        model.addAttribute("role", role);
        model.addAttribute("isAdmin", isAdmin); // Will be false for this path
        return "lecturer_comment_view";
    }

    @GetMapping("/assessment/{assessmentId}/group/{groupId}/ai-audit")
    @ResponseBody
    public ResponseEntity<?> auditTeamDynamics(
            @PathVariable("role") String role,
            @PathVariable("assessmentId") Long assessmentId,
            @PathVariable("groupId") Long groupId,
            Authentication auth) {

        if (aiServiceClient == null) {
            return ResponseEntity.status(503).body(Map.of("message", "AI service is not configured."));
        }

        Optional<Assessment> assessmentOpt = assessmentService.getAssessmentById(assessmentId);
        Optional<Group> groupOpt = groupId == null ? Optional.empty() : groupRepository.findById(groupId);

        if (assessmentOpt.isEmpty() || groupOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Assessment or Group not found."));
        }

        Assessment assessment = assessmentOpt.get();
        Group group = groupOpt.get();
        boolean isAdmin = "admin".equalsIgnoreCase(role);

        if (isAdmin) {
            Set<Long> managedCourseIds = getManagedCourseIdsForAdmin(auth.getName());
            if (!assessmentInManagedCourses(assessment, managedCourseIds) || !groupInManagedCourses(group, managedCourseIds)
                    || !assessment.getCourse().getId().equals(group.getCourse().getId())) {
                return ResponseEntity.status(403).body(Map.of("message", "Unauthorized course access."));
            }
        } else {
            Optional<Lecturer> lecturerOpt = findLecturerByAuthName(auth.getName());
            if (lecturerOpt.isEmpty() || !isLecturerAssignedToAssessment(lecturerOpt.get(), assessment)
                    || group.getCourse() == null || !assessment.getCourse().getId().equals(group.getCourse().getId())) {
                return ResponseEntity.status(403).body(Map.of("message", "Unauthorized assessment access."));
            }
        }

        List<Student> students = resolveStudentsForGroup(group);
        List<AiTeamDynamicsDtos.MemberOverview> memberOverviews = students.stream()
            .map(s -> new AiTeamDynamicsDtos.MemberOverview(
                s.getId(),
                (s.getUsername() != null && !s.getUsername().trim().isEmpty()) ? s.getUsername() : s.getEmail(),
                s.getEmail()
            ))
            .collect(Collectors.toList());

        List<AiTeamDynamicsDtos.PeerRatingItem> peerRatings = new java.util.ArrayList<>();
        if (markRepository != null && !students.isEmpty()) {
            List<Mark> marks = markRepository.findByGroupMembersAndAssessment(students, assessment);
            for (Mark m : marks) {
                if (m.getEvaluatorStudent() != null && m.getEvaluatedStudent() != null) {
                    double earned = m.getMarkValue() != null ? m.getMarkValue() : 0.0;
                    double possible = (m.getSubRubric() != null && m.getSubRubric().getMarks() != null)
                            ? m.getSubRubric().getMarks().doubleValue()
                            : (m.getRubric() != null && m.getRubric().getMarks() != null
                                    ? m.getRubric().getMarks().doubleValue()
                                    : 100.0);
                    String criterionLabel = m.getSubRubric() != null ? m.getSubRubric().getName() : (m.getRubric() != null ? m.getRubric().getName() : "Criterion");
                    peerRatings.add(new AiTeamDynamicsDtos.PeerRatingItem(
                        m.getEvaluatorStudent().getId(),
                        m.getEvaluatorStudent().getUsername() != null ? m.getEvaluatorStudent().getUsername() : m.getEvaluatorStudent().getEmail(),
                        m.getEvaluatedStudent().getId(),
                        m.getEvaluatedStudent().getUsername() != null ? m.getEvaluatedStudent().getUsername() : m.getEvaluatedStudent().getEmail(),
                        criterionLabel,
                        earned,
                        possible
                    ));
                }
            }
        }

        List<AiTeamDynamicsDtos.PeerCommentItem> peerComments = new java.util.ArrayList<>();
        for (Student student : students) {
            String studentName = (student.getUsername() != null && !student.getUsername().trim().isEmpty()) ? student.getUsername() : student.getEmail();
            Map<String, Map<String, List<String>>> commentsMap = calculateService.getAllAssessmentComments(student, assessment);
            for (Map.Entry<String, Map<String, List<String>>> qEntry : commentsMap.entrySet()) {
                String question = qEntry.getKey();
                for (Map.Entry<String, List<String>> eEntry : qEntry.getValue().entrySet()) {
                    String evaluatorName = eEntry.getKey();
                    for (String text : eEntry.getValue()) {
                        peerComments.add(new AiTeamDynamicsDtos.PeerCommentItem(
                            evaluatorName,
                            studentName,
                            question,
                            text
                        ));
                    }
                }
            }
        }

        AiTeamDynamicsDtos.AuditRequest request = new AiTeamDynamicsDtos.AuditRequest(
            group.getId(),
            group.getGroupName(),
            assessment.getId(),
            assessment.getTitle(),
            memberOverviews,
            peerRatings,
            peerComments
        );

        try {
            var response = aiServiceClient.auditTeamDynamics(request);
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of("message", "AI Audit Error: " + ex.getMessage()));
        }
    }
}