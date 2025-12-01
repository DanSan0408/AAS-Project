package com.capstone.adproject.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.capstone.adproject.model.IndustrialSupervisor;

@Repository
public interface IndustrialSupervisorRepository extends JpaRepository<IndustrialSupervisor, Long> {
    Optional<IndustrialSupervisor> findByUsername(String username);

    Optional<IndustrialSupervisor> findByEmail(String email);

    // **New Method for Forgot Password - Find by Token**
    Optional<IndustrialSupervisor> findByResetPasswordToken(String resetPasswordToken);
    
    boolean existsByUsername(String username);
    
    boolean existsByEmail(String email);
}
