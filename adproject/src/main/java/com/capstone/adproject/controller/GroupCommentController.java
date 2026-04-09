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
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping; // Import Logger
import org.springframework.web.servlet.mvc.support.RedirectAttributes; // Import LoggerFactory

import com.capstone.adproject.model.AdminCourseAssignment;
import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.repositories.AdminCourseAssignmentRepository;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.CalculateService;
import com.capstone.adproject.service.CourseScopeService;

@Controller
@RequestMapping("/{role}/comments/view")
public class GroupCommentController {

    private static final Logger logger = LoggerFactory.getLogger(GroupCommentController.class); // Add logger

    private final AssessmentService assessmentService;
    private final GroupRepository groupRepository;
    private final LecturerRepository lecturerRepository;
    private final AdminCourseAssignmentRepository adminCourseAssignmentRepository;
    private final CalculateService calculateService;
    private final CourseScopeService courseScopeService;

    public GroupCommentController(
            AssessmentService assessmentService,
            GroupRepository groupRepository,
            LecturerRepository lecturerRepository,
            AdminCourseAssignmentRepository adminCourseAssignmentRepository,
            CalculateService calculateService,
            CourseScopeService courseScopeService) {
        this.assessmentService = assessmentService;
        this.groupRepository = groupRepository;
        this.lecturerRepository = lecturerRepository;
        this.adminCourseAssignmentRepository = adminCourseAssignmentRepository;
        this.calculateService = calculateService;
        this.courseScopeService = courseScopeService;
    }

    private Set<Long> getManagedCourseIdsForAdmin(String adminEmail) {
        Set<Long> activeCourseIds = courseScopeService.getActiveCourseIdsForCurrentUser();
        if (!activeCourseIds.isEmpty()) {
            return activeCourseIds;
        }

        Optional<Lecturer> lecturerOpt = lecturerRepository.findByEmail(adminEmail)
            .or(() -> lecturerRepository.findByUsername(adminEmail));

        if (lecturerOpt.isEmpty()) {
            return Set.of();
        }

        Lecturer lecturer = lecturerOpt.get();
        Set<Long> managedCourseIds = adminCourseAssignmentRepository.findByLecturerId(lecturer.getId()).stream()
            .map(AdminCourseAssignment::getCourse)
            .filter(c -> c != null && c.getId() != null)
            .map(c -> c.getId())
            .collect(Collectors.toCollection(HashSet::new));

        if (managedCourseIds.isEmpty() && lecturer.getCourse() != null && lecturer.getCourse().getId() != null) {
            managedCourseIds.add(lecturer.getCourse().getId());
        }

        return managedCourseIds;
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
            Set<Long> managedCourseIds = getManagedCourseIdsForAdmin(auth.getName());
            model.addAttribute("assessments", assessmentService.findAllAssessmentsWithRubrics().stream()
                .filter(a -> assessmentInManagedCourses(a, managedCourseIds))
                .collect(Collectors.toList()));
        } else {
            model.addAttribute("assessments", assessmentService.findAllAssessmentsWithRubrics());
        }
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("role", role);
        if (isAdmin) {
            return "comment_select_assessment"; // Admin uses existing template
        } else {
            return "lecturer_comment_select_assessment"; // Lecturer uses new template
        }
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
            logger.info("selectGroup: Admin context. Fetching all groups."); // Debug log
            groups = groupRepository.findAll().stream()
                .filter(g -> groupInManagedCourses(g, managedCourseIds))
                .collect(Collectors.toList());
        } else {
            String username = auth.getName();
            Optional<Lecturer> lecturerOpt = lecturerRepository.findByEmail(username)
                .or(() -> lecturerRepository.findByUsername(username));
            
            if (lecturerOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Lecturer profile not found.");
                logger.warn("selectGroup: Lecturer profile not found for username: {}", username);
                return "redirect:/";
            }
            Lecturer lecturer = lecturerOpt.get();

            // Filter groups where the current lecturer is either Academic or Industrial Supervisor
            groups = groupRepository.findAll().stream()
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
        if (!assessmentInManagedCourses(assessment, managedCourseIds) || !groupInManagedCourses(group, managedCourseIds)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Unauthorized access to another course's comments.");
            return "redirect:/" + role + "/comments/view/assessments";
        }
        List<Student> students = group.getStudents();
        
        // Fetch comments using existing CalculateService which properly identifies Evaluators (Peer/Lecturer)
        Map<Long, Map<String, List<String>>> groupCommentsMap = new HashMap<>();
        Map<Long, Map<String, List<String>>> individualCommentsMap = new HashMap<>();

        for (Student student : students) {
            groupCommentsMap.put(student.getId(), calculateService.getGroupAssessmentComments(student, assessment));
            individualCommentsMap.put(student.getId(), calculateService.getIndividualAssessmentComments(student, assessment));
        }

        model.addAttribute("assessment", assessment);
        model.addAttribute("group", group);
        model.addAttribute("students", students);
        model.addAttribute("groupCommentsMap", groupCommentsMap);
        model.addAttribute("individualCommentsMap", individualCommentsMap);
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
        Optional<Lecturer> lecturerOpt = lecturerRepository.findByEmail(username)
            .or(() -> lecturerRepository.findByUsername(username));
        
        if (lecturerOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lecturer profile not found.");
            logger.warn("viewLecturerComments: Lecturer profile not found for username: {}", username);
            return "redirect:/";
        }
        Lecturer lecturer = lecturerOpt.get();
        
        if (assessment.getCourse() == null || lecturer.getCourse() == null || 
            !assessment.getCourse().getId().equals(lecturer.getCourse().getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to view comments for this assessment.");
            return "redirect:/" + role + "/comments/view/assessments";
        }

        // Filter groups where the current lecturer is either Academic or Industrial Supervisor
        List<Group> supervisedGroups = groupRepository.findAll().stream()
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
        Map<Group, Map<Student, Map<String, List<String>>>> commentsByGroupAndStudent = new LinkedHashMap<>();

        for (Group group : supervisedGroups) {
            Map<Student, Map<String, List<String>>> commentsByStudent = new LinkedHashMap<>();
            for (Student student : group.getStudents()) {
                Map<String, List<String>> studentComments = new HashMap<>();
                studentComments.put("groupComments", calculateService.getGroupAssessmentComments(student, assessment).values().stream().flatMap(List::stream).collect(Collectors.toList()));
                studentComments.put("individualComments", calculateService.getIndividualAssessmentComments(student, assessment).values().stream().flatMap(List::stream).collect(Collectors.toList()));
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
}