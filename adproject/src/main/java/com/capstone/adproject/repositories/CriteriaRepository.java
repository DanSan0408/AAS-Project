package com.capstone.adproject.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

import com.capstone.adproject.model.Criteria;

// No need for @Repository annotation, JpaRepository handles it
@Repository
public interface CriteriaRepository extends JpaRepository<Criteria, Long> {

    boolean existsByNameAndAssessmentId(String name, Long assessmentId);

    Optional<Criteria> findByNameAndAssessmentId(String name, Long assessmentId);
}