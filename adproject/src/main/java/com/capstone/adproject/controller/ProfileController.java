package com.capstone.adproject.controller;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.capstone.adproject.model.Admin;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.SuperAdmin;
import com.capstone.adproject.repositories.AdminRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.StudentRepository;
import com.capstone.adproject.repositories.SuperAdminRepository;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;
    private final SuperAdminRepository superAdminRepository;
    private final AdminRepository adminRepository;

    public ProfileController(StudentRepository studentRepository, LecturerRepository lecturerRepository, SuperAdminRepository superAdminRepository, AdminRepository adminRepository) {
        this.studentRepository = studentRepository;
        this.lecturerRepository = lecturerRepository;
        this.superAdminRepository = superAdminRepository;
        this.adminRepository = adminRepository;
    }

    @GetMapping
    public String viewProfile(Authentication authentication, Model model) {
        String username = authentication.getName();
        String role = getRole(authentication);
        
        String displayUsername = "";
        String displayEmail = "";
        
        if ("STUDENT".equals(role)) {
            Optional<Student> studentOpt = studentRepository.findByEmail(username).or(() -> studentRepository.findByUsername(username));
            if (studentOpt.isPresent()) {
                displayUsername = studentOpt.get().getUsername();
                displayEmail = studentOpt.get().getEmail();
            }
        } else if ("LECTURER".equals(role)) {
            Optional<Lecturer> lecturerOpt = lecturerRepository.findByEmail(username).or(() -> lecturerRepository.findByUsername(username));
            if (lecturerOpt.isPresent()) {
                displayUsername = lecturerOpt.get().getUsername();
                displayEmail = lecturerOpt.get().getEmail();
            }
        } else if ("ADMIN".equals(role)) {
            Optional<Admin> adminOpt = adminRepository.findByEmail(username).or(() -> adminRepository.findByUsername(username));
            if (adminOpt.isPresent()) {
                displayUsername = adminOpt.get().getUsername();
                displayEmail = adminOpt.get().getEmail();
            }
        } else if ("SUPER_ADMIN".equals(role)) {
            Optional<SuperAdmin> superAdminOpt = superAdminRepository.findByEmail(username).or(() -> superAdminRepository.findByUsername(username));
            if (superAdminOpt.isPresent()) {
                displayUsername = superAdminOpt.get().getUsername();
                displayEmail = superAdminOpt.get().getEmail();
            }
        }

        model.addAttribute("username", displayUsername);
        model.addAttribute("email", displayEmail);
        model.addAttribute("role", role);
        
        return "profile";
    }

    @PostMapping("/update")
    @Transactional
    public String updateProfile(@RequestParam("username") String newUsername, Authentication authentication, RedirectAttributes redirectAttributes) {
        String authName = authentication.getName();
        String role = getRole(authentication);
        boolean isUpdated = false;
        
        try {
            if ("STUDENT".equals(role)) {
                Optional<Student> studentOpt = studentRepository.findByEmail(authName).or(() -> studentRepository.findByUsername(authName));
                if (studentOpt.isPresent()) {
                    Student student = studentOpt.get();
                    student.setUsername(newUsername);
                    studentRepository.save(student);
                    isUpdated = true;
                }
            } else if ("LECTURER".equals(role)) {
                Optional<Lecturer> lecturerOpt = lecturerRepository.findByEmail(authName).or(() -> lecturerRepository.findByUsername(authName));
                if (lecturerOpt.isPresent()) {
                    Lecturer lecturer = lecturerOpt.get();
                    lecturer.setUsername(newUsername);
                    lecturerRepository.save(lecturer);
                    isUpdated = true;
                }
            } else if ("ADMIN".equals(role)) {
                Optional<Admin> adminOpt = adminRepository.findByEmail(authName).or(() -> adminRepository.findByUsername(authName));
                if (adminOpt.isPresent()) {
                    Admin admin = adminOpt.get();
                    admin.setUsername(newUsername);
                    adminRepository.save(admin);
                    isUpdated = true;
                }
            } else if ("SUPER_ADMIN".equals(role)) {
                Optional<SuperAdmin> superAdminOpt = superAdminRepository.findByEmail(authName).or(() -> superAdminRepository.findByUsername(authName));
                if (superAdminOpt.isPresent()) {
                    SuperAdmin superAdmin = superAdminOpt.get();
                    superAdmin.setUsername(newUsername);
                    superAdminRepository.save(superAdmin);
                    isUpdated = true;
                }
            }
            
            if (isUpdated) {
                redirectAttributes.addFlashAttribute("successMessage", "Profile updated successfully!");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Error: Could not locate your account record to update.");
            }
        } catch (DataIntegrityViolationException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error: Username might already be in use.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating profile: " + e.getMessage());
        }
        
        return "redirect:/profile";
    }

    private String getRole(Authentication authentication) {
        if (authentication != null && authentication.getAuthorities() != null) {
            if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"))) {
                return "SUPER_ADMIN";
            }
            if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                return "ADMIN";
            }
            if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_LECTURER"))) {
                return "LECTURER";
            }
            if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT"))) {
                return "STUDENT";
            }
        }
        return "UNKNOWN";
    }
}