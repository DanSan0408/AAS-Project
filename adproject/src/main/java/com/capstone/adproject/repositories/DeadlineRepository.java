package com.capstone.adproject.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.capstone.adproject.model.Deadline;

@Repository
public interface DeadlineRepository extends JpaRepository<Deadline, Long> {
    
    Optional<Deadline> findByTitle(String title);
    
    List<Deadline> findByAssessorType(String assessorType);
    
    List<Deadline> findByAssessmentId(Long assessmentId);
    
    List<Deadline> findByAssessmentIdAndAssessorType(Long assessmentId, String assessorType);
}