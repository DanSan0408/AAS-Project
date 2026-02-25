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

    @Override
    public UserDetails loadUserByUsername(String emailOrUsername) throws UsernameNotFoundException {
        
        Optional<Student> student = studentRepository.findByEmail(emailOrUsername);
        if (student.isPresent()) {
            return new User(
                student.get().getEmail(),
                student.get().getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_STUDENT"))
            );
        }
        
        student = studentRepository.findByUsername(emailOrUsername);
        if (student.isPresent()) {
            return new User(
                student.get().getEmail() != null ? student.get().getEmail() : student.get().getUsername(),
                student.get().getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_STUDENT"))
            );
        }

        Optional<Lecturer> lecturer = lecturerRepository.findByEmail(emailOrUsername);
        if (lecturer.isPresent()) {
            return new User(
                lecturer.get().getEmail(),
                lecturer.get().getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_LECTURER"))
            );
        }
        
        lecturer = lecturerRepository.findByUsername(emailOrUsername);
        if (lecturer.isPresent()) {
            return new User(
                lecturer.get().getEmail() != null ? lecturer.get().getEmail() : lecturer.get().getUsername(),
                lecturer.get().getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_LECTURER"))
            );
        }

        Optional<IndustrialSupervisor> supervisor = industrialSupervisorRepository.findByEmail(emailOrUsername);
        if (supervisor.isPresent()) {
            return new User(
                supervisor.get().getEmail(),
                supervisor.get().getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))
            );
        }
        
        supervisor = industrialSupervisorRepository.findByUsername(emailOrUsername);
        if (supervisor.isPresent()) {
            return new User(
                supervisor.get().getEmail() != null ? supervisor.get().getEmail() : supervisor.get().getUsername(),
                supervisor.get().getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_SUPERVISOR"))
            );
        }

        Optional<Admin> admin = adminRepository.findByEmail(emailOrUsername);
        if (admin.isPresent()) {
            return new User(
                admin.get().getEmail(),  
                admin.get().getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
            );
        }
        
        admin = adminRepository.findByUsername(emailOrUsername);
        if (admin.isPresent()) {
            return new User(
                admin.get().getEmail() != null ? admin.get().getEmail() : admin.get().getUsername(),
                admin.get().getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
            );
        }

        throw new UsernameNotFoundException("User not found with email/username: " + emailOrUsername);
    }
}