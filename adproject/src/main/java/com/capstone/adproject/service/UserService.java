package com.capstone.adproject.service;

import com.capstone.adproject.model.Admin;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.IndustrialSupervisor;
import com.capstone.adproject.repositories.AdminRepository;
import com.capstone.adproject.repositories.StudentRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.IndustrialSupervisorRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class UserService {

    private final AdminRepository adminRepo;
    private final StudentRepository studentRepo;
    private final LecturerRepository lecturerRepo;
    private final IndustrialSupervisorRepository supervisorRepo;
    private final PasswordEncoder passwordEncoder;

    public UserService(AdminRepository adminRepo, StudentRepository studentRepo, LecturerRepository lecturerRepo, IndustrialSupervisorRepository supervisorRepo, PasswordEncoder passwordEncoder) {
        this.adminRepo = adminRepo;
        this.studentRepo = studentRepo;
        this.lecturerRepo = lecturerRepo;
        this.supervisorRepo = supervisorRepo;
        this.passwordEncoder = passwordEncoder;
    }

   public Object findUserByEmail(String email) {
    // Use orElse(null) to handle the Optional return type
    return adminRepo.findByEmail(email)
        .map(u -> (Object)u)
        .orElseGet(() -> studentRepo.findByEmail(email)
            .map(u -> (Object)u)
            .orElseGet(() -> lecturerRepo.findByEmail(email)
                .map(u -> (Object)u)
                .orElseGet(() -> supervisorRepo.findByEmail(email)
                    .map(u -> (Object)u)
                    .orElse(null))));
}

public Object findUserByResetToken(String token) {
    // Use orElse(null) to handle the Optional return type
    return adminRepo.findByResetPasswordToken(token)
        .map(u -> (Object)u)
        .orElseGet(() -> studentRepo.findByResetPasswordToken(token)
            .map(u -> (Object)u)
            .orElseGet(() -> lecturerRepo.findByResetPasswordToken(token)
                .map(u -> (Object)u)
                .orElseGet(() -> supervisorRepo.findByResetPasswordToken(token)
                    .map(u -> (Object)u)
                    .orElse(null))));
}

    public String generateResetToken(Object user) {
        String token = UUID.randomUUID().toString();
        
        // Use pattern matching to set the token based on the user type
        if (user instanceof Admin admin) {
            admin.setResetPasswordToken(token);
            adminRepo.save(admin);
        } else if (user instanceof Student student) {
            student.setResetPasswordToken(token);
            studentRepo.save(student);
        } else if (user instanceof Lecturer lecturer) {
            lecturer.setResetPasswordToken(token);
            lecturerRepo.save(lecturer);
        } else if (user instanceof IndustrialSupervisor supervisor) {
            supervisor.setResetPasswordToken(token);
            supervisorRepo.save(supervisor);
        }
        return token;
    }

    public void updatePassword(Object user, String newPassword) {
        String encodedPassword = passwordEncoder.encode(newPassword);
        
        // Update password and clear the token
        if (user instanceof Admin admin) {
            admin.setPassword(encodedPassword);
            admin.setResetPasswordToken(null);
            adminRepo.save(admin);
        } else if (user instanceof Student student) {
            student.setPassword(encodedPassword);
            student.setResetPasswordToken(null);
            studentRepo.save(student);
        } else if (user instanceof Lecturer lecturer) {
            lecturer.setPassword(encodedPassword);
            lecturer.setResetPasswordToken(null);
            lecturerRepo.save(lecturer);
        } else if (user instanceof IndustrialSupervisor supervisor) {
            supervisor.setPassword(encodedPassword);
            supervisor.setResetPasswordToken(null);
            supervisorRepo.save(supervisor);
        }
    }
}