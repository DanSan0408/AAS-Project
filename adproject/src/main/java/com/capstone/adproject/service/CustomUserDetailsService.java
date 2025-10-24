package com.capstone.adproject.service;

import java.util.Collections;
import java.util.Optional;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
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

    private final AdminRepository adminRepository;
    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;
    private final IndustrialSupervisorRepository industrialSupervisorRepository;

    //Constructor
    public CustomUserDetailsService(
            AdminRepository adminRepository,
            StudentRepository studentRepository,
            LecturerRepository lecturerRepository,
            IndustrialSupervisorRepository industrialSupervisorRepository) {
        this.adminRepository = adminRepository;
        this.studentRepository = studentRepository;
        this.lecturerRepository = lecturerRepository;
        this.industrialSupervisorRepository = industrialSupervisorRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Attempt to find the user in each repository
        Optional<Admin> admin = adminRepository.findByUsername(username);
        if (admin.isPresent()) {
            return new org.springframework.security.core.userdetails.User(
                    admin.get().getUsername(),
                    admin.get().getPassword(),
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
            );
        }

        Optional<Student> student = studentRepository.findByUsername(username);
        if (student.isPresent()) {
            return new org.springframework.security.core.userdetails.User(
                    student.get().getUsername(),
                    student.get().getPassword(),
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_STUDENT"))
            );
        }

        Optional<Lecturer> lecturer = lecturerRepository.findByUsername(username);
        if (lecturer.isPresent()) {
            return new org.springframework.security.core.userdetails.User(
                    lecturer.get().getUsername(),
                    lecturer.get().getPassword(),
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_LECTURER"))
            );
        }

        Optional<IndustrialSupervisor> supervisor = industrialSupervisorRepository.findByUsername(username);
        if (supervisor.isPresent()) {
            return new org.springframework.security.core.userdetails.User(
                    supervisor.get().getUsername(),
                    supervisor.get().getPassword(),
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))
            );
        }

        throw new UsernameNotFoundException("User not found with username: " + username);
    }
}
