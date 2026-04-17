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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.capstone.adproject.model.Course;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.service.SuperAdminService;
import java.util.Optional;

@Controller
@RequestMapping("/superadmin")
public class SuperAdminController {

    private final SuperAdminService superAdminService;
    private final LecturerRepository lecturerRepository;

    public SuperAdminController(SuperAdminService superAdminService, LecturerRepository lecturerRepository) {
        this.superAdminService = superAdminService;
        this.lecturerRepository = lecturerRepository;
    }

    private String getLoggedInUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            return authentication.getName();
        }
        return "Super Admin";
    }

    @GetMapping("/home")
    public String superAdminHome(Model model) {
        String username = getLoggedInUsername();
        model.addAttribute("superAdminUsername", username);

        var managedCourses = superAdminService.getManagedCoursesForAdminEmail(username);
        model.addAttribute("managedCourses", managedCourses);
        model.addAttribute("managedCourseCount", managedCourses.size());
        
        // Get the assigned course for this user
        var assignedCourse = superAdminService.getUserCourse(username);
        if (assignedCourse.isPresent()) {
            model.addAttribute("assignedCourse", assignedCourse.get());
        } else {
            model.addAttribute("assignedCourse", new Course());
        }
        
        // Get all courses with their statistics
        var allCourses = superAdminService.getAllCourses();
        var courseStatistics = allCourses.stream()
            .map(course -> superAdminService.getCourseSummary(course.getId()))
            .toList();
        model.addAttribute("courseStatistics", courseStatistics);
        
        return "superadmin_home";
    }

    @GetMapping("/manage-courses")
    public String manageCourses(Model model) {
        model.addAttribute("courses", superAdminService.getAllCourses());
        model.addAttribute("newCourse", new Course());
        model.addAttribute("superAdminUsername", getLoggedInUsername());
        return "manage_courses";
    }

    @PostMapping("/courses/add")
    public String addCourse(@ModelAttribute Course course, RedirectAttributes redirectAttributes) {
        try {
            Optional<com.capstone.adproject.model.Lecturer> currentLecturer = lecturerRepository.findByEmail(getLoggedInUsername());
            if (currentLecturer.isPresent()) {
                course.setCreatedBy(currentLecturer.get());
            }

            superAdminService.saveCourse(course);
            redirectAttributes.addFlashAttribute("successMessage", "Course added successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error adding course: " + e.getMessage());
        }
        return "redirect:/superadmin/manage-courses";
    }

    @GetMapping("/courses/edit/{id}")
    public String editCourse(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        return superAdminService.getCourseById(id).map(course -> {
            model.addAttribute("course", course);
            model.addAttribute("superAdminUsername", getLoggedInUsername());
            return "edit_course"; // A new template for editing a course
        }).orElseGet(() -> {
            redirectAttributes.addFlashAttribute("errorMessage", "Course not found.");
            return "redirect:/superadmin/manage-courses";
        });
    }

    @PostMapping("/courses/update")
    public String updateCourse(@ModelAttribute Course course, RedirectAttributes redirectAttributes) {
        try {
            superAdminService.saveCourse(course);
            redirectAttributes.addFlashAttribute("successMessage", "Course updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating course: " + e.getMessage());
        }
        return "redirect:/superadmin/manage-courses";
    }

    @PostMapping("/courses/delete/{id}")
    public String deleteCourse(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            superAdminService.deleteCourse(id);
            redirectAttributes.addFlashAttribute("successMessage", "Course deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting course: " + e.getMessage());
        }
        return "redirect:/superadmin/manage-courses";
    }

    @GetMapping("/invite-admin")
    public String inviteAdminForm(Model model) {
        model.addAttribute("courses", superAdminService.getAllCourses());
        model.addAttribute("superAdminUsername", getLoggedInUsername());
        return "invite_admin"; // A new template for inviting admins
    }

    @PostMapping("/invite-admin")
    public String inviteAdmin(@RequestParam String email, @RequestParam String name, @RequestParam Long courseId, RedirectAttributes redirectAttributes) {
        superAdminService.inviteAdmin(email, name, courseId);
        redirectAttributes.addFlashAttribute("successMessage", "Admin invitation sent to " + email);
        return "redirect:/superadmin/invite-admin";
    }
}