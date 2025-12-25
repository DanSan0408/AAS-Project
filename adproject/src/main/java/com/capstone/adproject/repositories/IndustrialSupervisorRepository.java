package com.capstone.adproject.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // 1. Unassign as Industrial Supervisor from Groups
    @Modifying
    @Query("UPDATE Group g SET g.industrialSupervisor = null WHERE g.industrialSupervisor.id = :supervisorId")
    void unlinkFromGroups(@Param("supervisorId") Long supervisorId);

    // 2. Delete marks given by this supervisor
    // Note: Assuming Mark table uses 'supervisorId' column based on your CalculateService
    @Modifying
    @Query("DELETE FROM Mark m WHERE m.supervisorId = :supervisorId")
    void deleteMarksGiven(@Param("supervisorId") Long supervisorId);
}
