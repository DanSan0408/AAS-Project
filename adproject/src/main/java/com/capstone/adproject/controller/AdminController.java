package com.capstone.adproject.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function; 

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.capstone.adproject.dto.GroupAssignmentDto;
import com.capstone.adproject.model.Admin;
import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Criteria;
import com.capstone.adproject.model.IndustrialSupervisor;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.service.AdminService;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.DeadlineService;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;
    private final AssessmentService assessmentService;
    private final DeadlineService deadlineService;

    //constructor
    public AdminController(AdminService adminService, AssessmentService assessmentService, DeadlineService deadlineService) {
        this.adminService = adminService;
        this.assessmentService = assessmentService;
        this.deadlineService = deadlineService;
    }


    // Existing helper method for login username
    private String getLoggedInUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Admin) {
            return ((Admin) authentication.getPrincipal()).getUsername();
        }
        return "Admin";
    }

    // Existing helper method (still useful for single types, but we will rely more on the new grouping)
    private String getAssessmentKey(Assessment assessment, String type) {
        final String DEFAULT_EVAL_TYPE = "Ungrouped Assessments";
        final String DEFAULT_ASSESS_TYPE = "Miscellaneous";
        
        String key = "N/A_TEMP"; 
        
        // Use the first available rubric/criteria to determine the main assessment type header
        if (assessment.getRubrics() != null && !assessment.getRubrics().isEmpty()) {
            if ("evaluationType".equals(type)) {
                key = assessment.getRubrics().get(0).getEvaluationType();
            } else if ("assessmentType".equals(type)) {
                key = assessment.getRubrics().get(0).getAssessmentTypes();
            }
        }
        
        if (key.equals("N/A_TEMP") && assessment.getCriteria() != null && !assessment.getCriteria().isEmpty()) {
            if ("evaluationType".equals(type)) {
                key = assessment.getCriteria().get(0).getEvaluationType();
            } else if ("assessmentType".equals(type)) {
                key = assessment.getCriteria().get(0).getAssessmentTypes(); 
            }
        }
        
        if (key.equals("N/A_TEMP")) {
            return "evaluationType".equals(type) ? DEFAULT_EVAL_TYPE : DEFAULT_ASSESS_TYPE;
        }
        
        return key;
    }
    
    // HELPER METHOD FOR NESTED GROUPING OF RUBRICS/CRITERIA
    public Map<String, Map<String, List<Object>>> groupAssessmentComponents(Assessment assessment) {
        
        Map<String, Map<String, List<Object>>> grouped = new LinkedHashMap<>();
        
        // 1. Group Likert Rubrics
        if (assessment.getRubrics() != null) {
            for (Rubric rubric : assessment.getRubrics()) {
                String evalType = rubric.getEvaluationType();
                String assessType = rubric.getAssessmentTypes();
                
                grouped.computeIfAbsent(evalType, k -> new LinkedHashMap<>())
                       .computeIfAbsent(assessType, k -> new ArrayList<>())
                       .add(rubric);
            }
        }

        // 2. Group Criteria
        if (assessment.getCriteria() != null) {
            for (Criteria criteria : assessment.getCriteria()) {
                String evalType = criteria.getEvaluationType();
                String assessType = criteria.getAssessmentTypes();
                
                grouped.computeIfAbsent(evalType, k -> new LinkedHashMap<>())
                       .computeIfAbsent(assessType, k -> new ArrayList<>())
                       .add(criteria);
            }
        }
        
        return grouped;
    }


    //hantar admin ke home admin and fetch data for assessments and deadlines
    @GetMapping("/home")
    public String adminHome(Model model) {
    
        List<Assessment> allAssessments = assessmentService.findAllAssessmentsWithRubrics(); 

        // Pass the two required helper methods
        BiFunction<Assessment, String, String> keyExtractor = this::getAssessmentKey;
        model.addAttribute("getAssessmentKey", keyExtractor); 
        
        Function<Assessment, Map<String, Map<String, List<Object>>>> componentGrouper = this::groupAssessmentComponents;
        model.addAttribute("groupAssessmentComponents", componentGrouper); 
        
        model.addAttribute("allAssessments", allAssessments); 
        
        model.addAttribute("adminUsername", getLoggedInUsername());
        model.addAttribute("deadlines", deadlineService.getAllDeadlines()); 
        return "admin_home";
    }

    // =========================================================
    // USER MANAGEMENT START
    // =========================================================

    // NEW METHOD: This was missing and caused the 404 error if you tried to navigate here directly.
    @GetMapping("/manage-users")
    public String manageUsers(Model model) {
        model.addAttribute("adminUsername", getLoggedInUsername());
        // NOTE: If your template is in src/main/resources/templates/admin/manage_users.html,
        // you MUST change the return value to "admin/manage_users".
        return "manage_users"; 
    }

    // The rest of the methods remain unchanged but are included for completeness.
    
    @GetMapping("/group-assignment")
    public String groupAssignmentPage(Model model) {
        model.addAttribute("adminUsername", getLoggedInUsername());
        
        // Data needed for the form
        model.addAttribute("groupAssignmentDto", new GroupAssignmentDto());
        model.addAttribute("availableStudents", adminService.getStudentsWithoutGroup());
        model.addAttribute("availableLecturers", adminService.getAllLecturers());
        model.addAttribute("availableSupervisors", adminService.getAllIndustrialSupervisors());
        
        // Data to display existing groups
        model.addAttribute("allGroups", adminService.getAllGroups()); 
        
        return "group_assignment";
    }

    @PostMapping("/group-assignment")
    public String createAndAssignGroup(@ModelAttribute GroupAssignmentDto groupAssignmentDto) {
        // Use the service method to handle the logic
        adminService.assignStudentsToNewGroup(groupAssignmentDto);
        
        return "redirect:/admin/group-assignment";
    }

    
    //hantar admin ke manage-students page and tambah user
    @GetMapping("/manage-students")
    public String manageStudents(Model model) {
        model.addAttribute("students", adminService.getAllStudents());
        model.addAttribute("student", new Student());
        model.addAttribute("adminUsername", getLoggedInUsername());
        return "manage_students";
    }

    //redirect if admin save a new student
    @PostMapping("/manage-students")
    public String saveStudent(@ModelAttribute Student student) {
        adminService.saveStudent(student);
        return "redirect:/admin/manage-students";
    }

    //cam student but for lecturers
    @GetMapping("/manage-lecturers")
    public String manageLecturers(Model model) {
        model.addAttribute("lecturers", adminService.getAllLecturers());
        model.addAttribute("lecturer", new Lecturer());
        model.addAttribute("adminUsername", getLoggedInUsername());
        return "manage_lecturers";
    }

    @PostMapping("/manage-lecturers")
    public String saveLecturer(@ModelAttribute Lecturer lecturer) {
        adminService.saveLecturer(lecturer);
        return "redirect:/admin/manage-lecturers";
    }

    //cam student but for industrial supervisor
    @GetMapping("/manage-supervisors")
    public String manageSupervisors(Model model) {
        model.addAttribute("supervisors", adminService.getAllIndustrialSupervisors());
        model.addAttribute("industrialSupervisor", new IndustrialSupervisor());
        model.addAttribute("adminUsername", getLoggedInUsername());
        return "manage_supervisors";
    }

    @PostMapping("/manage-supervisors")
    public String saveIndustrialSupervisor(@ModelAttribute IndustrialSupervisor industrialSupervisor) {
        adminService.saveIndustrialSupervisor(industrialSupervisor);
        return "redirect:/admin/manage-supervisors";
    }
    
    //redirect balik lepas dh delete students/lecturer/industrial supervisor and delete the data
    @GetMapping("/delete-student/{id}")
    public String deleteStudent(@PathVariable Long id) {
        adminService.deleteStudentById(id);
        return "redirect:/admin/manage-students";
    }

    @GetMapping("/delete-lecturer/{id}")
    public String deleteLecturer(@PathVariable Long id) {
        adminService.deleteLecturerById(id);
        return "redirect:/admin/manage-lecturers";
        }

    @GetMapping("/delete-supervisor/{id}")
    public String deleteIndustrialSupervisor(@PathVariable Long id) {
        adminService.deleteIndustrialSupervisorById(id);
        return "redirect:/admin/manage-supervisors";
    }

    //check and fetch existing data for that student/lecturer/industrial supervisor and redirect back to page to edit
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
}