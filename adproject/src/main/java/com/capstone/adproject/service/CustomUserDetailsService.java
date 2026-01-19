package com.capstone.adproject.service;

import java.util.Collections;
import java.util.Optional;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.capstone.adproject.model.Admin;
import com.capstone.adproject.model.IndustrialSupervisor;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.repositories.AdminRepository;
import com.capstone.adproject.repositories.IndustrialSupervisorRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.StudentRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;
    private final IndustrialSupervisorRepository industrialSupervisorRepository;
    private final AdminRepository adminRepository;

    public CustomUserDetailsService(
            StudentRepository studentRepository,
            LecturerRepository lecturerRepository,
            IndustrialSupervisorRepository industrialSupervisorRepository,
            AdminRepository adminRepository) {
        this.studentRepository = studentRepository;
        this.lecturerRepository = lecturerRepository;
        this.industrialSupervisorRepository = industrialSupervisorRepository;
        this.adminRepository = adminRepository;
    }

    /**
     * ✅ FIXED: Load user by EMAIL (primary) or USERNAME (fallback) for ALL user types including Admin
     */
    @Override
    public UserDetails loadUserByUsername(String emailOrUsername) throws UsernameNotFoundException {
        
        // ==================== STUDENT ====================
        // Try EMAIL first (new users)
        Optional<Student> student = studentRepository.findByEmail(emailOrUsername);
        if (student.isPresent()) {
            return new User(
                student.get().getEmail(),
                student.get().getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_STUDENT"))
            );
        }
        
        // Fallback to USERNAME (existing users)
        student = studentRepository.findByUsername(emailOrUsername);
        if (student.isPresent()) {
            return new User(
                student.get().getEmail() != null ? student.get().getEmail() : student.get().getUsername(),
                student.get().getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_STUDENT"))
            );
        }

        // ==================== LECTURER ====================
        // Try EMAIL first
        Optional<Lecturer> lecturer = lecturerRepository.findByEmail(emailOrUsername);
        if (lecturer.isPresent()) {
            return new User(
                lecturer.get().getEmail(),
                lecturer.get().getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_LECTURER"))
            );
        }
        
        // Fallback to USERNAME
        lecturer = lecturerRepository.findByUsername(emailOrUsername);
        if (lecturer.isPresent()) {
            return new User(
                lecturer.get().getEmail() != null ? lecturer.get().getEmail() : lecturer.get().getUsername(),
                lecturer.get().getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_LECTURER"))
            );
        }

        // ==================== INDUSTRIAL SUPERVISOR ====================
        // Try EMAIL first
        Optional<IndustrialSupervisor> supervisor = industrialSupervisorRepository.findByEmail(emailOrUsername);
        if (supervisor.isPresent()) {
            return new User(
                supervisor.get().getEmail(),
                supervisor.get().getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))
            );
        }
        
        // Fallback to USERNAME
        supervisor = industrialSupervisorRepository.findByUsername(emailOrUsername);
        if (supervisor.isPresent()) {
            return new User(
                supervisor.get().getEmail() != null ? supervisor.get().getEmail() : supervisor.get().getUsername(),
                supervisor.get().getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))
            );
        }

        // ==================== ADMIN ====================
        // ✅ FIXED: Try EMAIL first (just like other user types)
        Optional<Admin> admin = adminRepository.findByEmail(emailOrUsername);
        if (admin.isPresent()) {
            return new User(
                admin.get().getEmail(),  // Use email as identifier
                admin.get().getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
            );
        }
        
        // Fallback to USERNAME (existing admins who login with username)
        admin = adminRepository.findByUsername(emailOrUsername);
        if (admin.isPresent()) {
            return new User(
                admin.get().getEmail() != null ? admin.get().getEmail() : admin.get().getUsername(),
                admin.get().getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
            );
        }

        // If no user found with email or username
        throw new UsernameNotFoundException("User not found with email/username: " + emailOrUsername);
    }
}