package com.capstone.adproject.repositories;

import com.capstone.adproject.model.Lecturer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LecturerRepository extends JpaRepository<Lecturer, Long> {
    Optional<Lecturer> findByUsername(String username);

    Optional<Lecturer> findByEmail(String email);

    // **New Method for Forgot Password - Find by Token**
    Optional<Lecturer> findByResetPasswordToken(String resetPasswordToken);
}
