package com.capstone.adproject.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.security.core.GrantedAuthority;
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
        if (emailOrUsername == null || emailOrUsername.trim().isEmpty()) {
            throw new UsernameNotFoundException("User not found: identifier is blank");
        }

        String identifier = emailOrUsername.trim();

        // 1. Strict Priority 1: Super Admin
        Optional<SuperAdmin> superAdmin = superAdminRepository.findByEmail(identifier);
        if (superAdmin.isEmpty()) {
            superAdmin = superAdminRepository.findByUsername(identifier);
        }
        if (superAdmin.isPresent()) {
            SuperAdmin sa = superAdmin.get();
            List<GrantedAuthority> authorities = new ArrayList<>();
            if (sa.getRoles() != null && !sa.getRoles().isBlank()) {
                Arrays.stream(sa.getRoles().split(","))
                        .map(String::trim)
                        .filter(role -> !role.isEmpty())
                        .map(SimpleGrantedAuthority::new)
                        .forEach(authorities::add);
            }
            if (authorities.stream().noneMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()))) {
                authorities.add(0, new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
            }
            String email = sa.getEmail() != null ? sa.getEmail() : sa.getUsername();
            return new User(email, sa.getPassword(), authorities);
        }

        // 2. Strict Priority 2: Admin
        Optional<Admin> admin = adminRepository.findByEmail(identifier);
        if (admin.isEmpty()) {
            admin = adminRepository.findByUsername(identifier);
        }
        if (admin.isPresent()) {
            Admin adm = admin.get();
            List<GrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));

            // If account is also a Lecturer, safely append lecturer roles (e.g. ROLE_LECTURER) without altering admin password
            Optional<Lecturer> linkedLecturer = lecturerRepository.findByEmail(identifier);
            if (linkedLecturer.isEmpty()) {
                linkedLecturer = lecturerRepository.findByUsername(identifier);
            }
            if (linkedLecturer.isPresent()) {
                Lecturer lec = linkedLecturer.get();
                if (lec.getRoles() != null && !lec.getRoles().isBlank()) {
                    Arrays.stream(lec.getRoles().split(","))
                            .map(String::trim)
                            .filter(role -> !role.isEmpty())
                            .filter(role -> authorities.stream().noneMatch(a -> a.getAuthority().equals(role)))
                            .map(SimpleGrantedAuthority::new)
                            .forEach(authorities::add);
                } else if (authorities.stream().noneMatch(a -> "ROLE_LECTURER".equals(a.getAuthority()))) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_LECTURER"));
                }
            }

            String email = adm.getEmail() != null ? adm.getEmail() : adm.getUsername();
            return new User(email, adm.getPassword(), authorities);
        }

        // 3. Strict Priority 3: Lecturer
        Optional<Lecturer> lecturer = lecturerRepository.findByEmail(identifier);
        if (lecturer.isEmpty()) {
            lecturer = lecturerRepository.findByUsername(identifier);
        }
        if (lecturer.isPresent()) {
            Lecturer lec = lecturer.get();
            List<GrantedAuthority> authorities = new ArrayList<>();
            if (lec.getRoles() != null && !lec.getRoles().isBlank()) {
                Arrays.stream(lec.getRoles().split(","))
                        .map(String::trim)
                        .filter(role -> !role.isEmpty())
                        .map(SimpleGrantedAuthority::new)
                        .forEach(authorities::add);
            }
            if (authorities.stream().noneMatch(a -> "ROLE_LECTURER".equals(a.getAuthority()))) {
                authorities.add(new SimpleGrantedAuthority("ROLE_LECTURER"));
            }
            String email = lec.getEmail() != null ? lec.getEmail() : lec.getUsername();
            return new User(email, lec.getPassword(), authorities);
        }

        // 4. Strict Priority 4: Student
        Optional<Student> student = studentRepository.findByEmail(identifier);
        if (student.isEmpty()) {
            student = studentRepository.findByUsername(identifier);
        }
        if (student.isPresent()) {
            Student stu = student.get();
            List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_STUDENT"));
            String email = stu.getEmail() != null ? stu.getEmail() : stu.getUsername();
            return new User(email, stu.getPassword(), authorities);
        }

        throw new UsernameNotFoundException("User not found with email/username: " + emailOrUsername);
    }

}