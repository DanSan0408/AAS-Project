package com.capstone.adproject.service;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.capstone.adproject.model.Admin;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.repositories.AdminRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.StudentRepository;

@Service
public class UserService {

    private final AdminRepository adminRepo;
    private final StudentRepository studentRepo;
    private final LecturerRepository lecturerRepo;
    private final PasswordEncoder passwordEncoder;

    public UserService(AdminRepository adminRepo, StudentRepository studentRepo, LecturerRepository lecturerRepo, PasswordEncoder passwordEncoder) {
        this.adminRepo = adminRepo;
        this.studentRepo = studentRepo;
        this.lecturerRepo = lecturerRepo;
        this.passwordEncoder = passwordEncoder;
    }

   public Object findUserByEmail(String email) {
    return adminRepo.findByEmail(email)
        .map(u -> (Object)u)
        .orElseGet(() -> studentRepo.findByEmail(email)
            .map(u -> (Object)u)
            .orElseGet(() -> lecturerRepo.findByEmail(email)
                .map(u -> (Object)u)
                .orElse(null)));
}

public Object findUserByResetToken(String token) {
    return adminRepo.findByResetPasswordToken(token)
        .map(u -> (Object)u)
        .orElseGet(() -> studentRepo.findByResetPasswordToken(token)
            .map(u -> (Object)u)
            .orElseGet(() -> lecturerRepo.findByResetPasswordToken(token)
                .map(u -> (Object)u)
                .orElse(null)));
}

    public String generateResetToken(Object user) {
        String token = UUID.randomUUID().toString();
        
        if (user instanceof Admin admin) {
            admin.setResetPasswordToken(token);
            adminRepo.save(admin);
        } else if (user instanceof Student student) {
            student.setResetPasswordToken(token);
            studentRepo.save(student);
        } else if (user instanceof Lecturer lecturer) {
            lecturer.setResetPasswordToken(token);
            lecturerRepo.save(lecturer);
        }
        return token;
    }

    public void updatePassword(Object user, String newPassword) {
        String encodedPassword = passwordEncoder.encode(newPassword);
        String email = null;

        if (user instanceof Admin admin) {
            email = admin.getEmail();
        } else if (user instanceof Student student) {
            email = student.getEmail();
        } else if (user instanceof Lecturer lecturer) {
            email = lecturer.getEmail();
        }

        if (email != null) {
            final String finalEmail = email;
            adminRepo.findByEmail(finalEmail).ifPresent(a -> {
                a.setPassword(encodedPassword);
                a.setResetPasswordToken(null);
                adminRepo.save(a);
            });
            studentRepo.findByEmail(finalEmail).ifPresent(s -> {
                s.setPassword(encodedPassword);
                s.setResetPasswordToken(null);
                studentRepo.save(s);
            });
            lecturerRepo.findByEmail(finalEmail).ifPresent(l -> {
                l.setPassword(encodedPassword);
                l.setResetPasswordToken(null);
                lecturerRepo.save(l);
            });
        }
    }
}
