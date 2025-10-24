package com.capstone.adproject.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.capstone.adproject.model.Admin;
import com.capstone.adproject.model.IndustrialSupervisor;
import com.capstone.adproject.model.Lecturer;
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


    private String getLoggedInUsername() {
        //SecurityContextHolder (spring class) stores details about the current logged in user
        //.getContext().getAuthentication dapatkan object for current user session
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        //check admin ke tak
        if (authentication != null && authentication.getPrincipal() instanceof Admin) {
            return ((Admin) authentication.getPrincipal()).getUsername();
        }
        return "Admin";
    }

    //hantar admin ke home admin and fetch data for assessments and deadlines
    @GetMapping("/home")
    public String adminHome(Model model) {
        model.addAttribute("adminUsername", getLoggedInUsername());
        model.addAttribute("assessments", assessmentService.findAllAssessmentsWithRubrics()); 
        model.addAttribute("deadlines", deadlineService.getAllDeadlines()); 
        return "admin_home";
    }

    //hantar admin ke manage_users page
    @GetMapping("/manage-users")
    public String manageUsers(Model model) {
        model.addAttribute("adminUsername", getLoggedInUsername());
        return "manage_users";
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
