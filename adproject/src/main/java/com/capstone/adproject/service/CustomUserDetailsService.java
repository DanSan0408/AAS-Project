package com.capstone.adproject.service;

import java.util.Arrays;
import java.util.Optional;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.capstone.adproject.model.Admin;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.SuperAdmin;
import com.capstone.adproject.repositories.AdminRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.StudentRepository;
import com.capstone.adproject.repositories.SuperAdminRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;
    private final AdminRepository adminRepository;
    private final SuperAdminRepository superAdminRepository;

    public CustomUserDetailsService(
            StudentRepository studentRepository,
            LecturerRepository lecturerRepository,
            AdminRepository adminRepository,
            SuperAdminRepository superAdminRepository) {
        this.studentRepository = studentRepository;
        this.lecturerRepository = lecturerRepository;
        this.adminRepository = adminRepository;
        this.superAdminRepository = superAdminRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String emailOrUsername) throws UsernameNotFoundException {
        java.util.List<org.springframework.security.core.GrantedAuthority> authorities = new java.util.ArrayList<>();
        String password = null;
        String email = null;

        Optional<SuperAdmin> superAdmin = superAdminRepository.findByEmail(emailOrUsername);
        if (superAdmin.isEmpty()) {
            superAdmin = superAdminRepository.findByUsername(emailOrUsername);
        }
        if (superAdmin.isPresent()) {
            if (superAdmin.get().getRoles() != null && !superAdmin.get().getRoles().isEmpty()) {
                Arrays.stream(superAdmin.get().getRoles().split(","))
                        .map(String::trim)
                        .map(SimpleGrantedAuthority::new)
                        .forEach(authorities::add);
            } else {
                authorities.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
            }
            password = superAdmin.get().getPassword();
            email = superAdmin.get().getEmail() != null ? superAdmin.get().getEmail() : superAdmin.get().getUsername();
        }

        Optional<Student> student = studentRepository.findByEmail(emailOrUsername);
        if (student.isEmpty()) {
            student = studentRepository.findByUsername(emailOrUsername);
        }
        if (student.isPresent()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_STUDENT"));
            password = student.get().getPassword();
            email = student.get().getEmail() != null ? student.get().getEmail() : student.get().getUsername();
        }

        Optional<Lecturer> lecturer = lecturerRepository.findByEmail(emailOrUsername);
        if (lecturer.isEmpty()) {
            lecturer = lecturerRepository.findByUsername(emailOrUsername);
        }
        if (lecturer.isPresent()) {
            // Load roles from the lecturer object (this includes ROLE_SUPER_ADMIN if assigned)
            if (lecturer.get().getRoles() != null && !lecturer.get().getRoles().isEmpty()) {
                Arrays.stream(lecturer.get().getRoles().split(","))
                      .map(String::trim).map(SimpleGrantedAuthority::new).forEach(authorities::add);
            } else {
                // If no roles defined, add default ROLE_LECTURER
                authorities.add(new SimpleGrantedAuthority("ROLE_LECTURER"));
            }
            if (password == null) {
                password = lecturer.get().getPassword();
                email = lecturer.get().getEmail() != null ? lecturer.get().getEmail() : lecturer.get().getUsername();
            }
        }

        Optional<Admin> admin = adminRepository.findByEmail(emailOrUsername);
        if (admin.isEmpty()) {
            admin = adminRepository.findByUsername(emailOrUsername);
        }
        if (admin.isPresent()) {
            // Only add ROLE_ADMIN if not already present from lecturer roles
            if (!authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }
            if (password == null) {
                password = admin.get().getPassword();
                email = admin.get().getEmail() != null ? admin.get().getEmail() : admin.get().getUsername();
            }
        }

        if (password == null) {
            throw new UsernameNotFoundException("User not found with email/username: " + emailOrUsername);
        }

        return new User(email, password, authorities);
    }

}