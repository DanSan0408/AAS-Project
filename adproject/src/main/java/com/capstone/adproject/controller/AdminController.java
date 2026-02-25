package com.capstone.adproject.controller;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.capstone.adproject.dto.AssessmentAssignmentDto;
import com.capstone.adproject.dto.GroupAssignmentDto;
import com.capstone.adproject.dto.RandomizationInputDto;
import com.capstone.adproject.model.Admin;
import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Deadline;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.IndustrialSupervisor;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.LecturerGroupAssignment;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.service.AdminService;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.DeadlineService;
import com.capstone.adproject.service.RubricService;

import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;
    private final AssessmentService assessmentService;
    private final DeadlineService deadlineService;
    private final RubricService rubricService;
    private final GroupRepository groupRepository;
    private final LecturerRepository lecturerRepository;
    private final LecturerGroupAssignmentRepository assignmentRepository;

    //constructor
    public AdminController(
            AdminService adminService, 
            AssessmentService assessmentService, 
            DeadlineService deadlineService,
            RubricService rubricService,
            GroupRepository groupRepository,
            LecturerRepository lecturerRepository,
            LecturerGroupAssignmentRepository assignmentRepository) {
        this.adminService = adminService;
        this.assessmentService = assessmentService;
        this.deadlineService = deadlineService;
        this.rubricService = rubricService;
        this.groupRepository = groupRepository;
        this.lecturerRepository = lecturerRepository;
        this.assignmentRepository = assignmentRepository;
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
        if (authentication != null && authentication.getPrincipal() instanceof Admin) {
            return ((Admin) authentication.getPrincipal()).getEmail();
        }
        return "Admin";
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

        List<Assessment> allAssessments = assessmentService.findAllAssessmentsWithRubrics();

        Function<Assessment, Map<String, Map<String, List<Object>>>> componentGrouper = this::groupAssessmentComponents;
        model.addAttribute("groupAssessmentComponents", componentGrouper);

        Function<Object, Boolean> rubricChecker = this::isRubricType;
        model.addAttribute("isRubricType", rubricChecker);

        model.addAttribute("allAssessments", allAssessments);
        model.addAttribute("adminUsername", getLoggedInUsername());
        model.addAttribute("deadlines", deadlineService.getAllDeadlines());

        if (!model.containsAttribute("deadlineToSave")) {
            model.addAttribute("deadlineToSave", new com.capstone.adproject.model.Deadline());
        }
        return "admin_home";
    }


    @GetMapping("/lecturer-assignments/{assessmentId}")
    @Transactional
    public String showLecturerAssignmentPage(
            @PathVariable Long assessmentId,
            Model model,
            RedirectAttributes redirectAttributes) {
        
        Assessment assessment = rubricService.findAssessmentById(assessmentId);
        if (assessment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Assessment not found");
            return "redirect:/admin/home";
        }
        
        //fetch all groups and all lecturers
        List<Group> allGroups = groupRepository.findAllWithStudents();
        List<Lecturer> allLecturers = lecturerRepository.findAll();
        
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
        
        //gabung all data to the model
        model.addAttribute("assessment", assessment);
        model.addAttribute("allGroups", allGroups);
        model.addAttribute("allLecturers", allLecturers);
        model.addAttribute("groupLecturerMap", groupLecturerMap);
        model.addAttribute("adminUsername", getLoggedInUsername());
        
        return "admin_assign_lecturers";
    }

    //save lecturer assignments
    @PostMapping("/lecturer-assignments/{assessmentId}/save")
    @Transactional
    public String saveLecturerAssignments(
            @PathVariable Long assessmentId,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes redirectAttributes) {
        
        try {
            Assessment assessment = rubricService.findAssessmentById(assessmentId);
            if (assessment == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Assessment not found");
                return "redirect:/admin/home";
            }
            
            // Delete old assignment existing untuk assessment
            assignmentRepository.deleteByAssessment(assessment);
            assignmentRepository.flush();
            
            // Temporary map to figure out which lecturers belong to which groups based on the form data.
            Map<Long, List<Long>> groupLecturerAssignments = new java.util.HashMap<>();
            
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                
                if (key.startsWith("group_") && key.contains("_lecturer_")) {
                    if (value == null || value.trim().isEmpty() || value.equals("none")) {
                        continue; // Skip empty assignments
                    }
                    
                    String[] parts = key.split("_");
                    Long groupId = Long.parseLong(parts[1]);
                    Long lecturerId = Long.parseLong(value);
                    
                    groupLecturerAssignments
                        .computeIfAbsent(groupId, k -> new ArrayList<>())
                        .add(lecturerId);
                }
            }

            List<LecturerGroupAssignment> assignments = new ArrayList<>();
            for (Map.Entry<Long, List<Long>> entry : groupLecturerAssignments.entrySet()) {
                Long groupId = entry.getKey();
                Group group = groupRepository.findById(groupId)
                    .orElseThrow(() -> new RuntimeException("Group not found: " + groupId));
                
                for (Long lecturerId : entry.getValue()) {
                    Lecturer lecturer = lecturerRepository.findById(lecturerId)
                        .orElseThrow(() -> new RuntimeException("Lecturer not found: " + lecturerId));
                    
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
        
        List<Student> unassignedStudents = adminService.getStudentsWithoutGroup();
        
        model.addAttribute("adminUsername", getLoggedInUsername());
        model.addAttribute("groupAssignmentDto", new GroupAssignmentDto());
        model.addAttribute("availableStudents", unassignedStudents); 
        model.addAttribute("availableLecturers", adminService.getAllLecturers());
        model.addAttribute("availableSupervisors", adminService.getAllIndustrialSupervisors());
        model.addAttribute("allGroups", adminService.getAllGroups());
        model.addAttribute("allStudents", adminService.getAllStudents());
        
        if (!model.containsAttribute("randomizationInput")) {
            model.addAttribute("randomizationInput", new RandomizationInputDto());
        }
        
        model.addAttribute("availableStudentsCount", unassignedStudents.size()); 
        
        return "group_assignment";
    }
    
    @PostMapping("/group-assignment")
    public String createAndAssignGroup(@ModelAttribute GroupAssignmentDto groupAssignmentDto) {
        adminService.assignStudentsToNewGroup(groupAssignmentDto);
        return "redirect:/admin/group-assignment";
    }
    
    @GetMapping("/group/edit/{groupId}")
    public String editGroupPage(@PathVariable Long groupId, Model model, RedirectAttributes redirectAttributes) {
        Optional<Group> groupOpt = adminService.findGroupById(groupId);
        if (groupOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Group not found.");
            return "redirect:/admin/group-assignment";
        }
        
        Group group = groupOpt.get();
        
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
        model.addAttribute("availableStudents", adminService.getStudentsWithoutGroup());
        model.addAttribute("availableLecturers", adminService.getAllLecturers());
        model.addAttribute("availableSupervisors", adminService.getAllIndustrialSupervisors());
        model.addAttribute("adminUsername", getLoggedInUsername());
        
        return "edit_group";
    }

    @PostMapping("/group/edit/{groupId}")
    public String updateGroup(@PathVariable Long groupId, 
                            @ModelAttribute GroupAssignmentDto dto,
                            RedirectAttributes redirectAttributes) {
        
        try {
            adminService.updateGroup(groupId, dto);
            redirectAttributes.addFlashAttribute("success", "Group updated successfully!");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", "Error updating group: " + e.getMessage());
        }
        
        return "redirect:/admin/group/edit/" + groupId; 
    }
    
    @GetMapping("/group/remove-student/{studentId}")
    public String removeStudentFromGroup(@PathVariable Long studentId, RedirectAttributes redirectAttributes) {
        
        Long groupId = adminService.findStudentById(studentId)
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
    public String deleteGroup(@PathVariable Long groupId, RedirectAttributes redirectAttributes) {
        try {
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

    @GetMapping("/manage-students")
public String manageStudents(Model model, @ModelAttribute("student") Student student) {
    model.addAttribute("students", adminService.getAllStudents());

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
    model.addAttribute("lecturers", adminService.getAllLecturers());
    
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


   @GetMapping("/manage-supervisors")
public String manageSupervisors(Model model, @ModelAttribute("industrialSupervisor") IndustrialSupervisor industrialSupervisor) {
    model.addAttribute("supervisors", adminService.getAllIndustrialSupervisors());

    if (industrialSupervisor == null || industrialSupervisor.getId() == null && 
        (industrialSupervisor.getEmail() == null || industrialSupervisor.getEmail().isEmpty())) {
        model.addAttribute("industrialSupervisor", new IndustrialSupervisor());
    } else {
        model.addAttribute("industrialSupervisor", industrialSupervisor);
    }
    
    model.addAttribute("adminUsername", getLoggedInUsername());
    return "manage_supervisors";
}
    @PostMapping("/manage-supervisors")
public String saveIndustrialSupervisor(
        @ModelAttribute IndustrialSupervisor industrialSupervisor,
        @RequestParam(value = "confirmDuplicate", defaultValue = "false") boolean confirmDuplicate,
        RedirectAttributes redirectAttributes,
        HttpServletRequest request) {
    try {
        boolean isUpdate = (industrialSupervisor.getId() != null);
        
        if (!confirmDuplicate) {
            String duplicateMessage = adminService.checkSupervisorEmailDuplicate(
                industrialSupervisor.getEmail(),
                industrialSupervisor.getId()
            );
            
            if (duplicateMessage != null) {
                redirectAttributes.addFlashAttribute("industrialSupervisor", industrialSupervisor);
                redirectAttributes.addFlashAttribute("duplicateWarning", duplicateMessage);
                redirectAttributes.addFlashAttribute("isDuplicate", true);
                return "redirect:/admin/manage-supervisors";
            }
        }
        
        adminService.saveIndustrialSupervisor(industrialSupervisor, request);
        
        if (isUpdate) {
            redirectAttributes.addFlashAttribute("successMessage", "Industrial Supervisor updated successfully!");
        } else {
            redirectAttributes.addFlashAttribute("successMessage", "Industrial Supervisor created successfully! Welcome email sent.");
        }
    } catch (DataIntegrityViolationException e) {
        redirectAttributes.addFlashAttribute("errorMessage", "Error: Email already exists.");
    }
    return "redirect:/admin/manage-supervisors";
}

    @GetMapping("/delete-student/{id}")
public String deleteStudent(@PathVariable Long id, RedirectAttributes redirectAttributes) {
    try {
        adminService.deleteStudentById(id);
        redirectAttributes.addFlashAttribute("successMessage", "Student deleted successfully!");
    } catch (Exception e) {
        redirectAttributes.addFlashAttribute("errorMessage", "Error deleting student: " + e.getMessage());
    }
    return "redirect:/admin/manage-students";
}

@GetMapping("/delete-lecturer/{id}")
public String deleteLecturer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
    try {
        adminService.deleteLecturerById(id);
        redirectAttributes.addFlashAttribute("successMessage", "Lecturer deleted successfully!");
    } catch (Exception e) {
        redirectAttributes.addFlashAttribute("errorMessage", "Error deleting lecturer: " + e.getMessage());
    }
    return "redirect:/admin/manage-lecturers";
}

@GetMapping("/delete-supervisor/{id}")
public String deleteIndustrialSupervisor(@PathVariable Long id, RedirectAttributes redirectAttributes) {
    try {
        adminService.deleteIndustrialSupervisorById(id);
        redirectAttributes.addFlashAttribute("successMessage", "Industrial Supervisor deleted successfully!");
    } catch (Exception e) {
        redirectAttributes.addFlashAttribute("errorMessage", "Error deleting supervisor: " + e.getMessage());
    }
    return "redirect:/admin/manage-supervisors";
}

    @GetMapping("/edit-student/{id}")
    public String editStudent(@PathVariable Long id, Model model) {
        adminService.findStudentById(id).ifPresent(student -> {
            model.addAttribute("student", student);
        });
        model.addAttribute("students", adminService.getAllStudents());
        model.addAttribute("adminUsername", getLoggedInUsername());
        return "manage_students";
    }

    @GetMapping("/edit-lecturer/{id}")
    public String editLecturer(@PathVariable Long id, Model model) {
        adminService.findLecturerById(id).ifPresent(lecturer -> {
            model.addAttribute("lecturer", lecturer);
        });
        model.addAttribute("lecturers", adminService.getAllLecturers());
        model.addAttribute("adminUsername", getLoggedInUsername());
        return "manage_lecturers";
    }

    @GetMapping("/edit-supervisor/{id}")
    public String editIndustrialSupervisor(@PathVariable Long id, Model model) {
        adminService.findIndustrialSupervisorById(id).ifPresent(supervisor -> {
            model.addAttribute("industrialSupervisor", supervisor);
        });
        model.addAttribute("supervisors", adminService.getAllIndustrialSupervisors());
        model.addAttribute("adminUsername", getLoggedInUsername());
        return "manage_supervisors";
    }

    @GetMapping("/group-assignment/randomize")
    public String showRandomizeForm(Model model) {
        model.addAttribute("adminUsername", getLoggedInUsername());
        model.addAttribute("randomizationInput", new RandomizationInputDto());
        model.addAttribute("availableStudentsCount", adminService.getStudentsWithoutGroup().size());
        
        return "randomize_groups_input"; 
    }

    @PostMapping("/group-assignment/randomize/preview")
    public String previewRandomGroups(@ModelAttribute RandomizationInputDto randomizationInput, 
                                        Model model,
                                        RedirectAttributes redirectAttributes) {

        int maxStudents = randomizationInput.getMaxStudentsPerGroup();
        long availableStudentsCount = adminService.getAvailableStudentsCount(); 

        if (maxStudents < 1) {
            redirectAttributes.addFlashAttribute("error", "Cannot randomize: the max group size is invalid.");
            return "redirect:/admin/group-assignment"; 
        }
        
        List<Student> unassignedStudents = adminService.getStudentsWithoutGroup();
        
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
        model.addAttribute("availableLecturers", adminService.getAllLecturers());
        model.addAttribute("availableSupervisors", adminService.getAllIndustrialSupervisors());
        
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
    public String showAssessmentAssignationForm(@PathVariable Long assessmentId, Model model, RedirectAttributes redirectAttributes) {
        
        Optional<Assessment> assessmentOpt = assessmentService.getAssessmentById(assessmentId);
        
        if (assessmentOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Assessment not found.");
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
        
        try {
            adminService.deleteStudentsByIds(ids);
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

        try {
            adminService.deleteLecturersByIds(ids);
            redirectAttributes.addFlashAttribute("successMessage", "Selected lecturers deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting lecturers: " + e.getMessage());
        }
        return "redirect:/admin/manage-lecturers";
    }

    @PostMapping("/bulk-delete-supervisors")
    public String bulkDeleteSupervisors(@RequestParam(name = "ids", required = false) List<Long> ids, RedirectAttributes redirectAttributes) {
        if (ids == null || ids.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No supervisors selected for deletion.");
            return "redirect:/admin/manage-supervisors";
        }

        try {
            adminService.deleteIndustrialSupervisorsByIds(ids);
            redirectAttributes.addFlashAttribute("successMessage", "Selected supervisors deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting supervisors: " + e.getMessage());
        }
        return "redirect:/admin/manage-supervisors";
    }
}