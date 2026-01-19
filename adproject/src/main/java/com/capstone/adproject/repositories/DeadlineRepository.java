package com.capstone.adproject.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.capstone.adproject.model.Deadline;

@Repository
public interface DeadlineRepository extends JpaRepository<Deadline, Long> {
    
    /**
     * Find a deadline by its title.
     * Used for duplicate checking.
     */
    Optional<Deadline> findByTitle(String title);
    
    /**
     * ⭐ CRITICAL: Find deadlines by assessor type
     * Used to filter deadlines for specific roles (STUDENT, LECTURER, SUPERVISOR)
     */
    List<Deadline> findByAssessorType(String assessorType);
    
    /**
     * ⭐ CRITICAL: Find deadlines by assessment ID
     * Used to find all deadlines related to a specific assessment
     */
    List<Deadline> findByAssessmentId(Long assessmentId);
    
    /**
     * ⭐ CRITICAL: Find deadlines by assessment ID and assessor type
     * This is the KEY method for checking if an assessment is open for a specific user role
     */
    List<Deadline> findByAssessmentIdAndAssessorType(Long assessmentId, String assessorType);
}