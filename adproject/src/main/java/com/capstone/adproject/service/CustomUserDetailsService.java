package com.capstone.adproject.service;

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
import com.capstone.adproject.repositories.AdminRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.StudentRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;
    private final AdminRepository adminRepository;

    public CustomUserDetailsService(
            StudentRepository studentRepository,
            LecturerRepository lecturerRepository,
            AdminRepository adminRepository) {
        this.studentRepository = studentRepository;
        this.lecturerRepository = lecturerRepository;
        this.adminRepository = adminRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String emailOrUsername) throws UsernameNotFoundException {
        java.util.List<org.springframework.security.core.GrantedAuthority> authorities = new java.util.ArrayList<>();
        String password = null;
        String email = null;

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
            authorities.add(new SimpleGrantedAuthority("ROLE_LECTURER"));
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
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
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