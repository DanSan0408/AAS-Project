package com.capstone.adproject.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.capstone.adproject.model.Admin;

@Repository
public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByUsername(String username);

    Optional<Admin> findByEmail(String email);

    // **New Method for Forgot Password - Find by Token**
    Optional<Admin> findByResetPasswordToken(String resetPasswordToken);
}
