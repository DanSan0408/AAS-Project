package com.capstone.adproject.controller;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.capstone.adproject.dto.AssessmentAssignmentDto;
import com.capstone.adproject.dto.GroupAssignmentDto;
import com.capstone.adproject.dto.RandomizationInputDto;
import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.Deadline;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.LecturerGroupAssignment;
import com.capstone.adproject.model.LecturerRubricAssignment;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.LecturerRubricAssignmentRepository;
import com.capstone.adproject.service.AdminService;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.CourseScopeService;
import com.capstone.adproject.service.CustomUserDetailsService;
import com.capstone.adproject.service.DeadlineService;
import com.capstone.adproject.service.RubricService;
import com.capstone.adproject.service.SuperAdminService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final CustomUserDetailsService userDetailsService;
    private final AdminService adminService;
    private final AssessmentService assessmentService;
    private final DeadlineService deadlineService;
    private final RubricService rubricService;
    private final SuperAdminService superAdminService;
    private final CourseScopeService courseScopeService;
    private final GroupRepository groupRepository;
    private final LecturerRepository lecturerRepository;
    private final LecturerGroupAssignmentRepository assignmentRepository;
    private final LecturerRubricAssignmentRepository rubricAssignmentRepository;

    //constructor
    public AdminController(
            AdminService adminService, 
            AssessmentService assessmentService, 
            DeadlineService deadlineService,
            RubricService rubricService,
            SuperAdminService superAdminService,
            CourseScopeService courseScopeService,
            GroupRepository groupRepository,
            LecturerRepository lecturerRepository,
            LecturerGroupAssignmentRepository assignmentRepository,
            LecturerRubricAssignmentRepository rubricAssignmentRepository,
            CustomUserDetailsService userDetailsService) {
        this.adminService = adminService;
        this.assessmentService = assessmentService;
        this.deadlineService = deadlineService;
        this.rubricService = rubricService;
        this.superAdminService = superAdminService;
        this.courseScopeService = courseScopeService;
        this.groupRepository = groupRepository;
        this.lecturerRepository = lecturerRepository;
        this.assignmentRepository = assignmentRepository;
        this.rubricAssignmentRepository = rubricAssignmentRepository;
        this.userDetailsService = userDetailsService;
    }

    //ensure that dates objects are converted as dates
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        dateFormat.setLenient(false);
        binder.registerCustomEditor(Date.class, new CustomDateEditor(dateFormat, true));
    }

    //extract the identitiy of the currently logged in user from spring security
    private String getLoggedInUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            return authentication.getName();
        }
        return "Admin";
    }

    private List<Course> getManagedCoursesForCurrentAdmin() {
        return courseScopeService.getManagedCoursesForCurrentUser();
    }

    private boolean isManagedCourseId(Long courseId) {
        return courseScopeService.isManagedCourseId(courseId);
    }

    private boolean ownsStudent(Student student) {
        return student != null
            && student.getCourse() != null
            && student.getCourse().getId() != null
            && courseScopeService.isActiveCourseId(student.getCourse().getId());
    }

    private boolean ownsLecturer(Lecturer lecturer) {
        return lecturer != null
            && lecturer.getCourse() != null
            && lecturer.getCourse().getId() != null
            && courseScopeService.isActiveCourseId(lecturer.getCourse().getId());
    }

    private boolean ownsGroup(Group group) {
        return group != null
            && group.getCourse() != null
            && group.getCourse().getId() != null
            && courseScopeService.isActiveCourseId(group.getCourse().getId());
    }

    private boolean ownsAssessment(Assessment assessment) {
        return assessment != null
            && assessment.getCourse() != null
            && assessment.getCourse().getId() != null
            && courseScopeService.isActiveCourseId(assessment.getCourse().getId());
    }

    @PostMapping("/switch-course")
    public String switchCourse(
            @RequestParam("courseId") Long courseId,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        boolean switched = courseScopeService.setActiveCourseIdForCurrentUser(courseId);
        if (!switched) {
            redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to switch to that course.");
            return "redirect:/admin/home";
        }

        HttpSession session = request.getSession(true);
        session.setAttribute(CourseScopeService.ACTIVE_COURSE_SESSION_KEY, courseId);

        redirectAttributes.addFlashAttribute("successMessage", "Active course switched successfully.");

        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank() && !referer.contains("/admin/switch-course")) {
            return "redirect:" + referer;
        }
        return "redirect:/admin/home";
    }

    @PostMapping("/api/set-course/{courseId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setCourseApi(@PathVariable("courseId") Long courseId) {
        Map<String, Object> body = new LinkedHashMap<>();
        boolean switched = courseScopeService.setActiveCourseIdForCurrentUser(courseId);
        if (!switched) {
            body.put("success", false);
            body.put("message", "You are not authorized to switch to that course.");
            return ResponseEntity.status(403).body(body);
        }

        Course activeCourse = courseScopeService.getActiveCourseForCurrentUser();
        body.put("success", true);
        body.put("activeCourseId", courseScopeService.getActiveCourseIdForCurrentUser());
        body.put("courseCode", activeCourse != null ? activeCourse.getCourseCode() : null);
        body.put("courseName", activeCourse != null ? activeCourse.getCourseName() : null);
        return ResponseEntity.ok(body);
    }

    private void refreshAuthentication(String email) {
        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            Authentication newAuth = new UsernamePasswordAuthenticationToken(
                userDetails, 
                userDetails.getPassword(), 
                userDetails.getAuthorities()
            );
            SecurityContextHolder.getContext().setAuthentication(newAuth);
            System.out.println("DEBUG: Authentication refreshed for " + email + ". New roles: " + userDetails.getAuthorities());
        } catch (Exception e) {
            System.err.println("DEBUG: Failed to refresh authentication: " + e.getMessage());
        }
    }

    @PostMapping("/assign-role")
    public String assignRole(
            @RequestParam("email") String email,
            @RequestParam("targetRole") String targetRole,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        System.out.println("DEBUG: assignRole called for email: " + email + ", role: " + targetRole);
        try {
            adminService.addRole(email, targetRole, request);
            
            // Refresh authentication if the user is assigning a role to themselves
            String loggedInEmail = getLoggedInUsername();
            if (loggedInEmail.equalsIgnoreCase(email)) {
                refreshAuthentication(email);
            }
            
            redirectAttributes.addFlashAttribute("successMessage", "User " + email + " successfully assigned " + targetRole + " role!");
        } catch (Exception e) {
            System.err.println("DEBUG: Role assignation error: " + e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Role assignation failed: " + e.getMessage());
        }
        
        String referer = request.getHeader("Referer");
        if (referer != null) {
            return "redirect:" + referer;
        }
        return "redirect:/admin/manage-users";
    }

    //transform data (objects) and reorganizes it
    public Map<String, Map<String, List<Object>>> groupAssessmentComponents(Assessment assessment) {
        Map<String, Map<String, List<Object>>> grouped = new LinkedHashMap<>();
        final String DUMMY_KEY = "ASSESSMENT_GROUPING";

        Map<String, List<Object>> innerGroup = new LinkedHashMap<>();

        if (assessment.getRubrics() != null) {
            for (Rubric rubric : assessment.getRubrics()) {
                String assessType = rubric.getAssessmentTypes();
                
                innerGroup.computeIfAbsent(assessType, k -> new ArrayList<>())
                          .add(rubric);
            }
        }
        
        grouped.put(DUMMY_KEY, innerGroup);
        return grouped;
    }

    //check component tu rubric ke tak
    public boolean isRubricType(Object component) {
        return component instanceof Rubric;
    }

    //gathering data from various services and pass it to admin_home
    @GetMapping("/home")
    public String adminHome(Model model) {
        List<Course> managedCourses = getManagedCoursesForCurrentAdmin();
        Long activeCourseId = courseScopeService.getActiveCourseIdForCurrentUser();
        Set<Long> managedCourseIds = managedCourses.stream()
            .map(Course::getId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        List<Assessment> allAssessments = activeCourseId == null
            ? List.of()
            : assessmentService.findAllAssessmentsWithRubricsByCourseId(activeCourseId).stream()
                .filter(this::ownsAssessment)
                .collect(Collectors.toList());

        Set<Long> managedAssessmentIds = allAssessments.stream()
            .map(Assessment::getId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        Function<Assessment, Map<String, Map<String, List<Object>>>> componentGrouper = this::groupAssessmentComponents;
        model.addAttribute("groupAssessmentComponents", componentGrouper);

        Function<Object, Boolean> rubricChecker = this::isRubricType;
        model.addAttribute("isRubricType", rubricChecker);

        model.addAttribute("allAssessments", allAssessments);
        model.addAttribute("adminUsername", getLoggedInUsername());
        model.addAttribute("deadlines", deadlineService.getAllDeadlines().stream()
            .filter(d -> d.getAssessmentId() != null && managedAssessmentIds.contains(d.getAssessmentId()))
            .collect(Collectors.toList()));

        model.addAttribute("managedCourses", managedCourses);
        model.addAttribute("managedCourseCount", managedCourseIds.size());
        model.addAttribute("activeCourseId", activeCourseId);
        model.addAttribute("activeCourse", courseScopeService.getActiveCourseForCurrentUser());

        if (!model.containsAttribute("deadlineToSave")) {
            model.addAttribute("deadlineToSave", new com.capstone.adproject.model.Deadline());
        }
        return "admin_home";
    }


    @GetMapping("/lecturer-assignments/{assessmentId}")
    @Transactional
    public String showLecturerAssignmentPage(
            @PathVariable("assessmentId") Long assessmentId,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        Assessment assessment = rubricService.findAssessmentById(assessmentId);
        if (assessment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Assessment not found");
            return "redirect:/admin/home";
        }
        if (!ownsAssessment(assessment)) {
            redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to access this assessment.");
            return "redirect:/admin/home";
        }
        
        //fetch all groups and all lecturers
        Long activeCourseId = courseScopeService.getActiveCourseIdForCurrentUser();
        List<Group> allGroups = activeCourseId == null
            ? List.of()
            : groupRepository.findAllWithStudentsByCourseId(activeCourseId).stream()
            .filter(this::ownsGroup)
            .collect(Collectors.toList());
        List<Lecturer> allLecturers = lecturerRepository.findAll().stream()
            .filter(this::ownsLecturer)
            .collect(Collectors.toList());
        
        //fetch assignments
        List<LecturerGroupAssignment> existingAssignments = 
            assignmentRepository.findByAssessment(assessment);
        
            //create map to track which lecturers are assigned to which group
        Map<Long, List<Lecturer>> groupLecturerMap = new java.util.HashMap<>();

        //loop through every group to findd it assigned lecturer
        for (Group group : allGroups) {
            List<Lecturer> assignedLecturers = existingAssignments.stream()
                .filter(a -> a.getGroup().getId().equals(group.getId()))
                .map(LecturerGroupAssignment::getLecturer)
                .collect(Collectors.toList());
            groupLecturerMap.put(group.getId(), assignedLecturers);
        }
        
        //fetch rubric assignments
        List<LecturerRubricAssignment> existingRubricAssignments = 
            rubricAssignmentRepository.findByAssessment(assessment);
            
        Map<Long, List<Lecturer>> rubricLecturerMap = new java.util.HashMap<>();
        if (assessment.getRubrics() != null) {
            for (Rubric rubric : assessment.getRubrics()) {
                List<Lecturer> assignedLecturers = existingRubricAssignments.stream()
                    .filter(a -> a.getRubric().getId().equals(rubric.getId()))
                    .map(LecturerRubricAssignment::getLecturer)
                    .collect(Collectors.toList());
                rubricLecturerMap.put(rubric.getId(), assignedLecturers);
            }
        }
        
        String assignmentMode = "GROUP";
        if (!existingRubricAssignments.isEmpty()) {
            assignmentMode = "RUBRIC";
            groupLecturerMap.clear(); // Enforce mutual exclusivity in the view
        } else {
            rubricLecturerMap.clear(); // Enforce mutual exclusivity in the view
        }
        model.addAttribute("assignmentMode", assignmentMode);
        
        //gabung all data to the model
        model.addAttribute("assessment", assessment);
        model.addAttribute("allGroups", allGroups);
        model.addAttribute("allLecturers", allLecturers);
        model.addAttribute("groupLecturerMap", groupLecturerMap);
        model.addAttribute("rubricLecturerMap", rubricLecturerMap);
        model.addAttribute("adminUsername", getLoggedInUsername());
        
        return "admin_assign_lecturers";
    }

    //save lecturer assignments
    @PostMapping("/lecturer-assignments/{assessmentId}/save")
    @Transactional
    public String saveLecturerAssignments(
            @PathVariable("assessmentId") Long assessmentId,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes) {
        
        try {
            Assessment assessment = rubricService.findAssessmentById(assessmentId);
            if (assessment == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Assessment not found");
                return "redirect:/admin/home";
            }
            if (!ownsAssessment(assessment)) {
                redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to modify this assessment.");
                return "redirect:/admin/home";
            }
            
            // Delete old assignment existing untuk assessment
            assignmentRepository.deleteByAssessment(assessment);
            assignmentRepository.flush();
            
            // Delete old rubric assignment existing untuk assessment
            rubricAssignmentRepository.deleteByAssessment(assessment);
            rubricAssignmentRepository.flush();
            
            // Temporary map to figure out which lecturers belong to which groups based on the form data.
            Map<Long, List<Long>> groupLecturerAssignments = new java.util.HashMap<>();
            Map<Long, List<Long>> rubricLecturerAssignments = new java.util.HashMap<>();
            
            String assignmentMode = allParams.getOrDefault("assignmentMode", "GROUP");
            
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                
                if ("GROUP".equals(assignmentMode) && key.startsWith("group_") && key.contains("_lecturer_")) {
                    if (value == null || value.trim().isEmpty() || value.equals("none")) {
                        continue; // Skip empty assignments
                    }
                    
                    String[] parts = key.split("_");
                    Long groupId = Long.valueOf(parts[1]);
                    Long lecturerId = Long.valueOf(value);
                    
                    groupLecturerAssignments
                        .computeIfAbsent(groupId, k -> new ArrayList<>())
                        .add(lecturerId);
                }
                else if ("RUBRIC".equals(assignmentMode) && key.startsWith("rubric_") && key.contains("_lecturer_")) {
                    if (value == null || value.trim().isEmpty() || value.equals("none")) {
                        continue; // Skip empty assignments
                    }
                    
                    String[] parts = key.split("_");
                    Long rubricId = Long.valueOf(parts[1]);
                    Long lecturerId = Long.valueOf(value);
                    
                    rubricLecturerAssignments
                        .computeIfAbsent(rubricId, k -> new ArrayList<>())
                        .add(lecturerId);
                }
            }

            if ("GROUP".equals(assignmentMode)) {
                List<LecturerGroupAssignment> assignments = new ArrayList<>();
                for (Map.Entry<Long, List<Long>> entry : groupLecturerAssignments.entrySet()) {
                    Long groupId = entry.getKey();
                    Group group = groupRepository.findById(groupId)
                        .orElseThrow(() -> new RuntimeException("Group not found: " + groupId));
                    if (!ownsGroup(group)) {
                        throw new RuntimeException("Unauthorized group access: " + groupId);
                    }
                    
                    for (Long lecturerId : entry.getValue()) {
                        Lecturer lecturer = lecturerRepository.findById(lecturerId)
                            .orElseThrow(() -> new RuntimeException("Lecturer not found: " + lecturerId));
                        if (!ownsLecturer(lecturer)) {
                            throw new RuntimeException("Unauthorized lecturer access: " + lecturerId);
                        }
                        
                        if (!assignmentRepository.existsByAssessmentAndGroupAndLecturer(assessment, group, lecturer)) {
                            LecturerGroupAssignment assignment = new LecturerGroupAssignment();
                            assignment.setAssessment(assessment);
                            assignment.setGroup(group);
                            assignment.setLecturer(lecturer);
                            assignments.add(assignment);
                        }
                    }
                }
                
                assignmentRepository.saveAll(assignments);
            }
            
            if ("RUBRIC".equals(assignmentMode)) {
                List<LecturerRubricAssignment> rubricAssignments = new ArrayList<>();
                for (Map.Entry<Long, List<Long>> entry : rubricLecturerAssignments.entrySet()) {
                    Long rubricId = entry.getKey();
                    Rubric rubric = assessment.getRubrics().stream()
                        .filter(r -> r.getId().equals(rubricId))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Rubric not found: " + rubricId));
                    
                    for (Long lecturerId : entry.getValue()) {
                        Lecturer lecturer = lecturerRepository.findById(lecturerId)
                            .orElseThrow(() -> new RuntimeException("Lecturer not found: " + lecturerId));
                        if (!ownsLecturer(lecturer)) {
                            throw new RuntimeException("Unauthorized lecturer access: " + lecturerId);
                        }
                        
                        LecturerRubricAssignment assignment = new LecturerRubricAssignment();
                        assignment.setAssessment(assessment);
                        assignment.setRubric(rubric);
                        assignment.setLecturer(lecturer);
                        rubricAssignments.add(assignment);
                    }
                }
                
                rubricAssignmentRepository.saveAll(rubricAssignments);
            }
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Lecturer assignments saved successfully for " + assessment.getTitle() + "!");
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                "Error saving assignments: " + e.getMessage());
            return "redirect:/admin/lecturer-assignments/" + assessmentId;
        }
        
        return "redirect:/rubrics/view/" + assessmentId;
    }

    @GetMapping("/group-assignment")
    public String groupAssignmentPage(Model model) {
        
        List<Student> unassignedStudents = adminService.getStudentsWithoutGroup().stream()
            .filter(this::ownsStudent)
            .collect(Collectors.toList());
        List<Lecturer> managedLecturers = adminService.getAllLecturers().stream()
            .filter(this::ownsLecturer)
            .collect(Collectors.toList());
        List<Group> managedGroups = adminService.getAllGroups().stream()
            .filter(this::ownsGroup)
            .collect(Collectors.toList());
        List<Student> managedStudents = adminService.getAllStudents().stream()
            .filter(this::ownsStudent)
            .collect(Collectors.toList());
        
        model.addAttribute("adminUsername", getLoggedInUsername());
        model.addAttribute("groupAssignmentDto", new GroupAssignmentDto());
        model.addAttribute("availableStudents", unassignedStudents); 
        model.addAttribute("availableLecturers", managedLecturers);
        model.addAttribute("allGroups", managedGroups);
        model.addAttribute("allStudents", managedStudents);
        
        if (!model.containsAttribute("randomizationInput")) {
            model.addAttribute("randomizationInput", new RandomizationInputDto());
        }
        
        model.addAttribute("availableStudentsCount", unassignedStudents.size()); 
        
        return "group_assignment";
    }
    
    @PostMapping("/group-assignment")
    public String createAndAssignGroup(@ModelAttribute GroupAssignmentDto groupAssignmentDto, RedirectAttributes redirectAttributes) {
        if (groupAssignmentDto.getSelectedStudentIds() != null) {
            boolean hasUnauthorizedStudents = groupAssignmentDto.getSelectedStudentIds().stream()
                .map(adminService::findStudentById)
                .anyMatch(studentOpt -> studentOpt.isEmpty() || !ownsStudent(studentOpt.get()));
            if (hasUnauthorizedStudents) {
                redirectAttributes.addFlashAttribute("error", "Cannot assign students from another admin's course.");
                return "redirect:/admin/group-assignment";
            }
        }

        if (groupAssignmentDto.getAcademicSupervisorId() != null) {
            Optional<Lecturer> lecturerOpt = adminService.findLecturerById(groupAssignmentDto.getAcademicSupervisorId());
            if (lecturerOpt.isEmpty() || !ownsLecturer(lecturerOpt.get())) {
                redirectAttributes.addFlashAttribute("error", "Cannot assign an academic supervisor outside your managed courses.");
                return "redirect:/admin/group-assignment";
            }
        }

        if (groupAssignmentDto.getIndustrialSupervisorId() != null) {
            Optional<Lecturer> lecturerOpt = adminService.findLecturerById(groupAssignmentDto.getIndustrialSupervisorId());
            if (lecturerOpt.isEmpty() || !ownsLecturer(lecturerOpt.get())) {
                redirectAttributes.addFlashAttribute("error", "Cannot assign an industrial supervisor outside your managed courses.");
                return "redirect:/admin/group-assignment";
            }
        }

        adminService.assignStudentsToNewGroup(groupAssignmentDto);
        return "redirect:/admin/group-assignment";
    }
    
    @GetMapping("/group/edit/{groupId}")
    public String editGroupPage(@PathVariable("groupId") Long groupId, Model model, RedirectAttributes redirectAttributes) {
        Optional<Group> groupOpt = adminService.findGroupById(groupId);
        if (groupOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Group not found.");
            return "redirect:/admin/group-assignment";
        }
        
        Group group = groupOpt.get();
        if (!ownsGroup(group)) {
            redirectAttributes.addFlashAttribute("error", "You are not authorized to edit this group.");
            return "redirect:/admin/group-assignment";
        }
        
        GroupAssignmentDto dto = new GroupAssignmentDto();
        dto.setGroupName(group.getGroupName());
        
        if (group.getAcademicSupervisor() != null) {
            dto.setAcademicSupervisorId(group.getAcademicSupervisor().getId());
        }
        if (group.getIndustrialSupervisor() != null) {
            dto.setIndustrialSupervisorId(group.getIndustrialSupervisor().getId());
        }
        
        model.addAttribute("group", group);
        model.addAttribute("groupAssignmentDto", dto); 
        model.addAttribute("studentsInGroup", adminService.getStudentsByGroup(group));
        model.addAttribute("availableStudents", adminService.getStudentsWithoutGroup().stream()
            .filter(this::ownsStudent)
            .collect(Collectors.toList()));
        model.addAttribute("availableLecturers", adminService.getAllLecturers().stream()
            .filter(this::ownsLecturer)
            .collect(Collectors.toList()));
        model.addAttribute("adminUsername", getLoggedInUsername());
        
        return "edit_group";
    }

    @PostMapping("/group/edit/{groupId}")
    public String updateGroup(@PathVariable("groupId") Long groupId, 
                            @ModelAttribute GroupAssignmentDto dto,
                            RedirectAttributes redirectAttributes) {
        
        try {
            Optional<Group> groupOpt = adminService.findGroupById(groupId);
            if (groupOpt.isEmpty() || !ownsGroup(groupOpt.get())) {
                redirectAttributes.addFlashAttribute("error", "You are not authorized to update this group.");
                return "redirect:/admin/group-assignment";
            }

            if (dto.getSelectedStudentIds() != null) {
                boolean hasUnauthorizedStudents = dto.getSelectedStudentIds().stream()
                    .map(adminService::findStudentById)
                    .anyMatch(studentOpt -> studentOpt.isEmpty() || !ownsStudent(studentOpt.get()));
                if (hasUnauthorizedStudents) {
                    redirectAttributes.addFlashAttribute("error", "Cannot add students from another admin's course.");
                    return "redirect:/admin/group/edit/" + groupId;
                }
            }

            if (dto.getAcademicSupervisorId() != null) {
                Optional<Lecturer> lecturerOpt = adminService.findLecturerById(dto.getAcademicSupervisorId());
                if (lecturerOpt.isEmpty() || !ownsLecturer(lecturerOpt.get())) {
                    redirectAttributes.addFlashAttribute("error", "Academic supervisor is outside your managed courses.");
                    return "redirect:/admin/group/edit/" + groupId;
                }
            }

            if (dto.getIndustrialSupervisorId() != null) {
                Optional<Lecturer> lecturerOpt = adminService.findLecturerById(dto.getIndustrialSupervisorId());
                if (lecturerOpt.isEmpty() || !ownsLecturer(lecturerOpt.get())) {
                    redirectAttributes.addFlashAttribute("error", "Industrial supervisor is outside your managed courses.");
                    return "redirect:/admin/group/edit/" + groupId;
                }
            }

            adminService.updateGroup(groupId, dto);
            redirectAttributes.addFlashAttribute("success", "Group updated successfully!");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", "Error updating group: " + e.getMessage());
        }
        
        return "redirect:/admin/group/edit/" + groupId; 
    }
    
    @GetMapping("/group/remove-student/{studentId}")
    public String removeStudentFromGroup(@PathVariable("studentId") Long studentId, RedirectAttributes redirectAttributes) {
        Optional<Student> studentOpt = adminService.findStudentById(studentId);
        if (studentOpt.isEmpty() || !ownsStudent(studentOpt.get())) {
            redirectAttributes.addFlashAttribute("error", "You are not authorized to modify this student.");
            return "redirect:/admin/group-assignment";
        }
        
        Long groupId = studentOpt
                                       .map(Student::getGroup)
                                       .map(Group::getId)
                                       .orElse(null);
        
        if (groupId == null) {
            redirectAttributes.addFlashAttribute("warning", "Student was not in a group or not found.");
            return "redirect:/admin/group-assignment";
        }
        
        adminService.removeStudentFromGroup(studentId);
        redirectAttributes.addFlashAttribute("success", "Student successfully removed from the group.");
        
        return "redirect:/admin/group/edit/" + groupId;
    }
    
    @PostMapping("/group/delete/{groupId}")
    public String deleteGroup(@PathVariable("groupId") Long groupId, RedirectAttributes redirectAttributes) {
        try {
            Optional<Group> groupOpt = adminService.findGroupById(groupId);
            if (groupOpt.isEmpty() || !ownsGroup(groupOpt.get())) {
                redirectAttributes.addFlashAttribute("error", "You are not authorized to delete this group.");
                return "redirect:/admin/group-assignment";
            }
            adminService.deleteGroupById(groupId); 
            redirectAttributes.addFlashAttribute("success", "Group ID " + groupId + " and all associated student assignments cleared successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error deleting group: " + e.getMessage());
        }
        return "redirect:/admin/group-assignment";
    }

    @GetMapping("/manage-users")
    public String manageUsers(Model model) {
        model.addAttribute("adminUsername", getLoggedInUsername());
        return "manage_users";
    }

    @GetMapping("/manage-courses")
    public String manageCourses(Model model) {
        model.addAttribute("courses", getManagedCoursesForCurrentAdmin());
        model.addAttribute("newCourse", new Course());
        model.addAttribute("adminUsername", getLoggedInUsername());
        return "admin_manage_courses";
    }

    @PostMapping("/courses/add")
    public String addCourse(@ModelAttribute Course course, RedirectAttributes redirectAttributes) {
        try {
            Course savedCourse = superAdminService.saveCourse(course);

            Optional<Lecturer> currentLecturer = lecturerRepository.findByEmail(getLoggedInUsername());
            if (currentLecturer.isPresent()) {
                superAdminService.assignAdminToCourse(currentLecturer.get().getId(), savedCourse.getId());
                redirectAttributes.addFlashAttribute("successMessage", "Course added and assigned to you successfully!");
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "Course added successfully, but could not auto-assign because your lecturer profile was not found.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error adding course: " + e.getMessage());
        }
        return "redirect:/admin/manage-courses";
    }

    @GetMapping("/courses/edit/{id}")
    public String editCourse(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        return superAdminService.getCourseById(id).map(course -> {
            if (!isManagedCourseId(course.getId())) {
                redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to edit this course.");
                return "redirect:/admin/manage-courses";
            }
            model.addAttribute("course", course);
            model.addAttribute("adminUsername", getLoggedInUsername());
            return "admin_edit_course";
        }).orElseGet(() -> {
            redirectAttributes.addFlashAttribute("errorMessage", "Course not found.");
            return "redirect:/admin/manage-courses";
        });
    }

    @PostMapping("/courses/update")
    public String updateCourse(@ModelAttribute Course course, RedirectAttributes redirectAttributes) {
        try {
            if (course.getId() == null || !isManagedCourseId(course.getId())) {
                redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to update this course.");
                return "redirect:/admin/manage-courses";
            }
            superAdminService.saveCourse(course);
            redirectAttributes.addFlashAttribute("successMessage", "Course updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating course: " + e.getMessage());
        }
        return "redirect:/admin/manage-courses";
    }

    @PostMapping("/courses/delete/{id}")
    public String deleteCourse(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            if (!isManagedCourseId(id)) {
                redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to delete this course.");
                return "redirect:/admin/manage-courses";
            }
            superAdminService.deleteCourse(id);
            redirectAttributes.addFlashAttribute("successMessage", "Course deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting course: " + e.getMessage());
        }
        return "redirect:/admin/manage-courses";
    }

    @GetMapping("/manage-students")
public String manageStudents(Model model, @ModelAttribute("student") Student student) {
    model.addAttribute("students", adminService.getAllStudents().stream()
        .filter(this::ownsStudent)
        .collect(Collectors.toList()));

    if (student == null || student.getId() == null && 
        (student.getEmail() == null || student.getEmail().isEmpty())) { 
        model.addAttribute("student", new Student());
    } else {
        model.addAttribute("student", student);
    }
    
    model.addAttribute("adminUsername", getLoggedInUsername());
    return "manage_students";
}

    @PostMapping("/manage-students")
public String saveStudent(
        @ModelAttribute Student student,
        @RequestParam(value = "confirmDuplicate", defaultValue = "false") boolean confirmDuplicate,
        RedirectAttributes redirectAttributes,
        HttpServletRequest request) {
    try {
        boolean isUpdate = (student.getId() != null);
        Long activeCourseId = courseScopeService.getActiveCourseIdForCurrentUser();

        if (activeCourseId == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "No active course selected. Please assign a course first.");
            return "redirect:/admin/manage-students";
        }

        if (isUpdate) {
            Optional<Student> existingStudentOpt = adminService.findStudentById(student.getId());
            if (existingStudentOpt.isEmpty() || !ownsStudent(existingStudentOpt.get())) {
                redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to update this student.");
                return "redirect:/admin/manage-students";
            }
        }
        
        if (!confirmDuplicate) {
            String duplicateMessage = adminService.checkStudentEmailDuplicate(
                student.getEmail(), 
                student.getId()
            );
            
            if (duplicateMessage != null) {
                redirectAttributes.addFlashAttribute("student", student);
                redirectAttributes.addFlashAttribute("duplicateWarning", duplicateMessage);
                redirectAttributes.addFlashAttribute("isDuplicate", true);
                return "redirect:/admin/manage-students";
            }
        }
        
        adminService.saveStudent(student, request);
        
        if (isUpdate) {
            redirectAttributes.addFlashAttribute("successMessage", "Student updated successfully!");
        } else {
            redirectAttributes.addFlashAttribute("successMessage", "Student created successfully! Welcome email sent.");
        }
    } catch (DataIntegrityViolationException e) {
        redirectAttributes.addFlashAttribute("errorMessage", "Error: Email already exists.");
    }
    return "redirect:/admin/manage-students";
}

    @GetMapping("/manage-lecturers")
public String manageLecturers(Model model, @ModelAttribute("lecturer") Lecturer lecturer) {
    model.addAttribute("lecturers", adminService.getAllLecturers().stream()
        .filter(this::ownsLecturer)
        .collect(Collectors.toList()));
    
    if (lecturer == null || lecturer.getId() == null && 
        (lecturer.getEmail() == null || lecturer.getEmail().isEmpty())) {
        model.addAttribute("lecturer", new Lecturer());
    } else {
        model.addAttribute("lecturer", lecturer);
    }
    
    model.addAttribute("adminUsername", getLoggedInUsername());
    return "manage_lecturers";
}

    @PostMapping("/manage-lecturers")
public String saveLecturer(
        @ModelAttribute Lecturer lecturer,
        @RequestParam(value = "confirmDuplicate", defaultValue = "false") boolean confirmDuplicate,
        RedirectAttributes redirectAttributes,
        HttpServletRequest request) {
    try {
        boolean isUpdate = (lecturer.getId() != null);
        Long activeCourseId = courseScopeService.getActiveCourseIdForCurrentUser();

        if (activeCourseId == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "No active course selected. Please assign a course first.");
            return "redirect:/admin/manage-lecturers";
        }

        if (isUpdate) {
            Optional<Lecturer> existingLecturerOpt = adminService.findLecturerById(lecturer.getId());
            if (existingLecturerOpt.isEmpty() || !ownsLecturer(existingLecturerOpt.get())) {
                redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to update this lecturer.");
                return "redirect:/admin/manage-lecturers";
            }
        }
        
        if (!confirmDuplicate) {
            String duplicateMessage = adminService.checkLecturerEmailDuplicate(
                lecturer.getEmail(),
                lecturer.getId()
            );
            
            if (duplicateMessage != null) {
                redirectAttributes.addFlashAttribute("lecturer", lecturer);
                redirectAttributes.addFlashAttribute("duplicateWarning", duplicateMessage);
                redirectAttributes.addFlashAttribute("isDuplicate", true);
                return "redirect:/admin/manage-lecturers";
            }
        }
        
        adminService.saveLecturer(lecturer, request);
        
        if (isUpdate) {
            redirectAttributes.addFlashAttribute("successMessage", "Lecturer updated successfully!");
        } else {
            redirectAttributes.addFlashAttribute("successMessage", "Lecturer created successfully! Welcome email sent.");
        }
    } catch (DataIntegrityViolationException e) {
        redirectAttributes.addFlashAttribute("errorMessage", "Error: Email already exists.");
    }
    return "redirect:/admin/manage-lecturers";
}



    @GetMapping("/delete-student/{id}")
public String deleteStudent(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
    try {
        Optional<Student> studentOpt = adminService.findStudentById(id);
        if (studentOpt.isEmpty() || !ownsStudent(studentOpt.get())) {
            redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to delete this student.");
            return "redirect:/admin/manage-students";
        }
        adminService.deleteStudentById(id);
        redirectAttributes.addFlashAttribute("successMessage", "Student deleted successfully!");
    } catch (Exception e) {
        redirectAttributes.addFlashAttribute("errorMessage", "Error deleting student: " + e.getMessage());
    }
    return "redirect:/admin/manage-students";
}

@GetMapping("/delete-lecturer/{id}")
public String deleteLecturer(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
    try {
        Optional<Lecturer> lecturerOpt = adminService.findLecturerById(id);
        if (lecturerOpt.isEmpty() || !ownsLecturer(lecturerOpt.get())) {
            redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to delete this lecturer.");
            return "redirect:/admin/manage-lecturers";
        }
        adminService.deleteLecturerById(id);
        redirectAttributes.addFlashAttribute("successMessage", "Lecturer deleted successfully!");
    } catch (Exception e) {
        redirectAttributes.addFlashAttribute("errorMessage", "Error deleting lecturer: " + e.getMessage());
    }
    return "redirect:/admin/manage-lecturers";
}


    @GetMapping("/edit-student/{id}")
    public String editStudent(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Student> studentOpt = adminService.findStudentById(id);
        if (studentOpt.isEmpty() || !ownsStudent(studentOpt.get())) {
            redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to edit this student.");
            return "redirect:/admin/manage-students";
        }
        model.addAttribute("student", studentOpt.get());
        model.addAttribute("students", adminService.getAllStudents().stream()
            .filter(this::ownsStudent)
            .collect(Collectors.toList()));
        model.addAttribute("adminUsername", getLoggedInUsername());
        return "manage_students";
    }

    @GetMapping("/edit-lecturer/{id}")
    public String editLecturer(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Lecturer> lecturerOpt = adminService.findLecturerById(id);
        if (lecturerOpt.isEmpty() || !ownsLecturer(lecturerOpt.get())) {
            redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to edit this lecturer.");
            return "redirect:/admin/manage-lecturers";
        }
        model.addAttribute("lecturer", lecturerOpt.get());
        model.addAttribute("lecturers", adminService.getAllLecturers().stream()
            .filter(this::ownsLecturer)
            .collect(Collectors.toList()));
        model.addAttribute("adminUsername", getLoggedInUsername());
        return "manage_lecturers";
    }


    @GetMapping("/group-assignment/randomize")
    public String showRandomizeForm(Model model) {
        int availableStudentsCount = (int) adminService.getStudentsWithoutGroup().stream()
            .filter(this::ownsStudent)
            .count();
        model.addAttribute("adminUsername", getLoggedInUsername());
        model.addAttribute("randomizationInput", new RandomizationInputDto());
        model.addAttribute("availableStudentsCount", availableStudentsCount);
        
        return "randomize_groups_input"; 
    }

    @PostMapping("/group-assignment/randomize/preview")
    public String previewRandomGroups(@ModelAttribute RandomizationInputDto randomizationInput, 
                                        Model model,
                                        RedirectAttributes redirectAttributes) {

        int maxStudents = randomizationInput.getMaxStudentsPerGroup();
        List<Student> unassignedStudents = adminService.getStudentsWithoutGroup().stream()
            .filter(this::ownsStudent)
            .collect(Collectors.toList());
        long availableStudentsCount = unassignedStudents.size(); 

        if (maxStudents < 1) {
            redirectAttributes.addFlashAttribute("error", "Cannot randomize: the max group size is invalid.");
            return "redirect:/admin/group-assignment"; 
        }
        
        if (unassignedStudents.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "No students are currently unassigned.");
            return "redirect:/admin/group-assignment"; 
        }
        
        Collections.shuffle(unassignedStudents);
        
        int actualGroupSize = Math.min(maxStudents, unassignedStudents.size());
        
        GroupAssignmentDto singleRandomGroup = new GroupAssignmentDto();
        singleRandomGroup.setGroupName("Random Group (Size: " + actualGroupSize + ")");
        
        List<Long> studentIds = unassignedStudents.subList(0, actualGroupSize).stream()
            .map(Student::getId)
            .collect(Collectors.toList());
            
        singleRandomGroup.setSelectedStudentIds(studentIds);

        Map<Long, Student> studentLookupMap = adminService.getAllStudents().stream()
            .filter(this::ownsStudent)
            .collect(Collectors.toMap(Student::getId, Function.identity()));
        
        // Add the comma-separated string for easier template processing
        String selectedStudentIdsStr = studentIds.stream()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
        
        model.addAttribute("randomGroupPreview", singleRandomGroup); 
        model.addAttribute("selectedStudentIdsStr", selectedStudentIdsStr);
        model.addAttribute("availableStudentsCount", availableStudentsCount);
        model.addAttribute("maxStudentsPerGroup", maxStudents);
        model.addAttribute("actualGroupSize", actualGroupSize);
        model.addAttribute("remainingStudents", availableStudentsCount - actualGroupSize);
        model.addAttribute("studentLookupMap", studentLookupMap); 
        model.addAttribute("availableStudentsForAdd", unassignedStudents); 
        model.addAttribute("availableLecturers", adminService.getAllLecturers().stream()
            .filter(this::ownsLecturer)
            .collect(Collectors.toList()));
        
        return "group_assignment_preview"; 
    }
    
    @PostMapping("/group-assignment/randomize/create") 
    public String createRandomGroups(
        @ModelAttribute("randomGroupPreview") GroupAssignmentDto groupToCreate, 
        RedirectAttributes redirectAttributes) {

        try {
            int count = adminService.createSingleGroupFromRandomization(groupToCreate); 
            redirectAttributes.addFlashAttribute("success", 
                "Successfully created and assigned students to **" + count + " new group**.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error creating group: " + e.getMessage());
        }

        return "redirect:/admin/group-assignment"; 
    }

    @GetMapping("/assessment/assign/{assessmentId}")
    public String showAssessmentAssignationForm(@PathVariable("assessmentId") Long assessmentId, Model model, RedirectAttributes redirectAttributes) {
        
        Optional<Assessment> assessmentOpt = assessmentService.getAssessmentById(assessmentId);
        
        if (assessmentOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Assessment not found.");
            return "redirect:/admin/home";
        }
        if (!ownsAssessment(assessmentOpt.get())) {
            redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to assign this assessment.");
            return "redirect:/admin/home";
        }

        AssessmentAssignmentDto dto = new AssessmentAssignmentDto();
        dto.setAssessmentId(assessmentId);
        dto.setTitle(assessmentOpt.get().getTitle() + " - Assignment"); 
        dto.setOpenType("INSTANT");

        model.addAttribute("adminUsername", getLoggedInUsername());
        model.addAttribute("assessment", assessmentOpt.get());
        model.addAttribute("assignmentDto", dto);
        model.addAttribute("assessorTypes", List.of("STUDENT", "LECTURER", "SUPERVISOR")); 
        
        return "assign_assessment";
    }

    @PostMapping("/assessment/assign")
public String assignAssessment(@ModelAttribute("assignmentDto") AssessmentAssignmentDto dto, RedirectAttributes redirectAttributes) {
    Optional<Assessment> assessmentOpt = dto.getAssessmentId() == null
        ? Optional.empty()
        : assessmentService.getAssessmentById(dto.getAssessmentId());
    if (assessmentOpt.isEmpty() || !ownsAssessment(assessmentOpt.get())) {
        redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to assign this assessment.");
        return "redirect:/admin/home";
    }
    
    if (dto.getAssessmentId() == null || dto.getAssessorType() == null || dto.getEndDate() == null || dto.getTitle() == null) {
         redirectAttributes.addFlashAttribute("errorMessage", "Missing required fields for assignment. Assessment ID, Assessor Type, Title, and End Date are mandatory.");
         return "redirect:/admin/assessment/assign/" + dto.getAssessmentId();
    }

    List<Deadline> existingDeadlines = deadlineService.getDeadlinesByAssessmentIdAndAssessorType(
        dto.getAssessmentId(), 
        dto.getAssessorType()
    );
    
    Deadline deadline;
    
    if (!existingDeadlines.isEmpty()) {
        deadline = existingDeadlines.get(0);

        if (existingDeadlines.size() > 1) {
            for (int i = 1; i < existingDeadlines.size(); i++) {
                deadlineService.deleteDeadline(existingDeadlines.get(i).getId());
            }
        }
    } else {
        deadline = new Deadline();
        deadline.setAssessmentId(dto.getAssessmentId());
        deadline.setAssessorType(dto.getAssessorType());
    }
    
    deadline.setTitle(dto.getTitle());
    deadline.setDate(dto.getEndDate());

    if ("INSTANT".equalsIgnoreCase(dto.getOpenType())) {
        deadline.setOpenDate(new Date()); 
    } else if ("SCHEDULED".equalsIgnoreCase(dto.getOpenType()) && dto.getOpenDate() != null) {
        deadline.setOpenDate(dto.getOpenDate());
    } else {
        deadline.setOpenDate(new Date()); 
    }

    try {
        deadlineService.save(deadline); 
        
        String openDateStr = new SimpleDateFormat("yyyy-MM-dd").format(deadline.getOpenDate());
        String action = existingDeadlines.isEmpty() ? "assigned" : "updated";

        redirectAttributes.addFlashAttribute("successMessage", 
            "Assessment " + dto.getTitle() + " " + action + " successfully for " + dto.getAssessorType() + "s. Open Date: " + openDateStr);
    } catch (Exception e) {
        redirectAttributes.addFlashAttribute("errorMessage", "Error assigning assessment: " + e.getMessage());
        return "redirect:/admin/assessment/assign/" + dto.getAssessmentId(); 
    }
    
    return "redirect:/admin/home"; 
}

    @PostMapping("/bulk-delete-students")
    public String bulkDeleteStudents(@RequestParam(name = "ids", required = false) List<Long> ids, RedirectAttributes redirectAttributes) {
        if (ids == null || ids.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No students selected for deletion.");
            return "redirect:/admin/manage-students";
        }

        List<Long> authorizedIds = ids.stream()
            .map(adminService::findStudentById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(this::ownsStudent)
            .map(Student::getId)
            .collect(Collectors.toList());

        if (authorizedIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No authorized students selected for deletion.");
            return "redirect:/admin/manage-students";
        }
        
        try {
            adminService.deleteStudentsByIds(authorizedIds);
            redirectAttributes.addFlashAttribute("successMessage", "Selected students deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting students: " + e.getMessage());
        }
        return "redirect:/admin/manage-students";
    }

    @PostMapping("/bulk-delete-lecturers")
    public String bulkDeleteLecturers(@RequestParam(name = "ids", required = false) List<Long> ids, RedirectAttributes redirectAttributes) {
        if (ids == null || ids.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No lecturers selected for deletion.");
            return "redirect:/admin/manage-lecturers";
        }

        List<Long> authorizedIds = ids.stream()
            .map(adminService::findLecturerById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(this::ownsLecturer)
            .map(Lecturer::getId)
            .collect(Collectors.toList());

        if (authorizedIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No authorized lecturers selected for deletion.");
            return "redirect:/admin/manage-lecturers";
        }

        try {
            adminService.deleteLecturersByIds(authorizedIds);
            redirectAttributes.addFlashAttribute("successMessage", "Selected lecturers deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting lecturers: " + e.getMessage());
        }
        return "redirect:/admin/manage-lecturers";
    }


    @PostMapping("/bulk-add-lecturers")
    public String bulkAddLecturers(@RequestParam("emailsText") String emailsText, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        try {
            int count = adminService.bulkAddLecturers(emailsText, request);
            redirectAttributes.addFlashAttribute("successMessage", count + " lecturers successfully added!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bulk add failed: " + e.getMessage());
        }
        return "redirect:/admin/manage-lecturers";
    }

    @PostMapping("/bulk-add-students")
    public String bulkAddStudents(@RequestParam("emailsText") String emailsText, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        try {
            int count = adminService.bulkAddStudents(emailsText, request);
            redirectAttributes.addFlashAttribute("successMessage", count + " students successfully added!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bulk add failed: " + e.getMessage());
        }
        return "redirect:/admin/manage-students";
    }

    @GetMapping("/search-lecturers")
    @ResponseBody
    public List<Lecturer> searchLecturers(@RequestParam("query") String query) {
        return adminService.searchLecturers(query).stream()
            .filter(this::ownsLecturer)
            .collect(Collectors.toList());
    }

}
