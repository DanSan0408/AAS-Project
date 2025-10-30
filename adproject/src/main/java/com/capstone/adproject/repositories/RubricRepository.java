package com.capstone.adproject.repositories;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.capstone.adproject.model.Rubric;
@Repository
public interface RubricRepository extends JpaRepository<Rubric, Long> {

    List<Rubric> findByNameAndAssessmentId(String name, Long assessmentId);
}
