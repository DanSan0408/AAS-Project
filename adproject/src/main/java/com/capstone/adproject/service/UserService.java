package com.capstone.adproject.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
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
public class UserService {

    private final AdminRepository adminRepo;
    private final StudentRepository studentRepo;
    private final LecturerRepository lecturerRepo;
    private final SuperAdminRepository superAdminRepo;
    private final PasswordEncoder passwordEncoder;

    public UserService(AdminRepository adminRepo, StudentRepository studentRepo, LecturerRepository lecturerRepo, SuperAdminRepository superAdminRepo, PasswordEncoder passwordEncoder) {
        this.adminRepo = adminRepo;
        this.studentRepo = studentRepo;
        this.lecturerRepo = lecturerRepo;
        this.superAdminRepo = superAdminRepo;
        this.passwordEncoder = passwordEncoder;
    }

    public Object findUserByEmail(String email) {
        return superAdminRepo.findByEmail(email)
            .map(u -> (Object)u)
            .orElseGet(() -> adminRepo.findByEmail(email)
            .map(u -> (Object)u)
            .orElseGet(() -> studentRepo.findByEmail(email)
                .map(u -> (Object)u)
                .orElseGet(() -> lecturerRepo.findByEmail(email)
                    .map(u -> (Object)u)
                    .orElse(null))));
    }

    public Object findUserByResetToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        String hashedToken = hashToken(token);
        // Look up by SHA-256 hash first
        Object user = superAdminRepo.findByResetPasswordToken(hashedToken)
            .map(u -> (Object)u)
            .orElseGet(() -> adminRepo.findByResetPasswordToken(hashedToken)
            .map(u -> (Object)u)
            .orElseGet(() -> studentRepo.findByResetPasswordToken(hashedToken)
                .map(u -> (Object)u)
                .orElseGet(() -> lecturerRepo.findByResetPasswordToken(hashedToken)
                    .map(u -> (Object)u)
                    .orElse(null))));

        // Fallback for legacy unhashed tokens (if any active before migration)
        if (user == null) {
            user = superAdminRepo.findByResetPasswordToken(token)
                .map(u -> (Object)u)
                .orElseGet(() -> adminRepo.findByResetPasswordToken(token)
                .map(u -> (Object)u)
                .orElseGet(() -> studentRepo.findByResetPasswordToken(token)
                    .map(u -> (Object)u)
                    .orElseGet(() -> lecturerRepo.findByResetPasswordToken(token)
                        .map(u -> (Object)u)
                        .orElse(null))));
        }

        if (user != null && isTokenExpired(user)) {
            return null;
        }
        return user;
    }

    private boolean isTokenExpired(Object user) {
        LocalDateTime expiry = null;
        if (user instanceof Admin a) expiry = a.getResetPasswordTokenExpiry();
        else if (user instanceof SuperAdmin sa) expiry = sa.getResetPasswordTokenExpiry();
        else if (user instanceof Student s) expiry = s.getResetPasswordTokenExpiry();
        else if (user instanceof Lecturer l) expiry = l.getResetPasswordTokenExpiry();

        if (expiry == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(expiry);
    }

    public String generateResetToken(Object user) {
        String rawToken = UUID.randomUUID().toString();
        String hashedToken = hashToken(rawToken);
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(30);
        
        if (user instanceof Admin admin) {
            admin.setResetPasswordToken(hashedToken);
            admin.setResetPasswordTokenExpiry(expiry);
            adminRepo.save(admin);
        } else if (user instanceof SuperAdmin superAdmin) {
            superAdmin.setResetPasswordToken(hashedToken);
            superAdmin.setResetPasswordTokenExpiry(expiry);
            superAdminRepo.save(superAdmin);
        } else if (user instanceof Student student) {
            student.setResetPasswordToken(hashedToken);
            student.setResetPasswordTokenExpiry(expiry);
            studentRepo.save(student);
        } else if (user instanceof Lecturer lecturer) {
            lecturer.setResetPasswordToken(hashedToken);
            lecturer.setResetPasswordTokenExpiry(expiry);
            lecturerRepo.save(lecturer);
        }
        return rawToken;
    }

    public void updatePassword(Object user, String newPassword) {
        String encodedPassword = passwordEncoder.encode(newPassword);
        String email = null;

        if (user instanceof Admin admin) {
            email = admin.getEmail();
        } else if (user instanceof SuperAdmin superAdmin) {
            email = superAdmin.getEmail();
        } else if (user instanceof Student student) {
            email = student.getEmail();
        } else if (user instanceof Lecturer lecturer) {
            email = lecturer.getEmail();
        }

        if (email != null) {
            final String finalEmail = email;
            superAdminRepo.findByEmail(finalEmail).ifPresent(sa -> {
                sa.setPassword(encodedPassword);
                sa.setResetPasswordToken(null);
                sa.setResetPasswordTokenExpiry(null);
                superAdminRepo.save(sa);
            });
            adminRepo.findByEmail(finalEmail).ifPresent(a -> {
                a.setPassword(encodedPassword);
                a.setResetPasswordToken(null);
                a.setResetPasswordTokenExpiry(null);
                adminRepo.save(a);
            });
            studentRepo.findByEmail(finalEmail).ifPresent(s -> {
                s.setPassword(encodedPassword);
                s.setResetPasswordToken(null);
                s.setResetPasswordTokenExpiry(null);
                studentRepo.save(s);
            });
            lecturerRepo.findByEmail(finalEmail).ifPresent(l -> {
                l.setPassword(encodedPassword);
                l.setResetPasswordToken(null);
                l.setResetPasswordTokenExpiry(null);
                l.setIsTempPassword(false); // Password is no longer temporary
                lecturerRepo.save(l);
            });
        }
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
