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

    Optional<IndustrialSupervisor> findByResetPasswordToken(String resetPasswordToken);
    
    boolean existsByUsername(String username);
    
    boolean existsByEmail(String email);

    
    @Modifying
    @Query("UPDATE Group g SET g.industrialSupervisor = null WHERE g.industrialSupervisor.id = :supervisorId")
    void unlinkFromGroups(@Param("supervisorId") Long supervisorId);

    @Modifying
    @Query("DELETE FROM Mark m WHERE m.supervisorId = :supervisorId")
    void deleteMarksGiven(@Param("supervisorId") Long supervisorId);

    @Modifying
    @Query("DELETE FROM AssessmentComment ac WHERE ac.evaluatorId = :supervisorId AND ac.evaluatorType = 'SUPERVISOR'")
    void deleteCommentsBySupervisor(@Param("supervisorId") Long supervisorId);
}