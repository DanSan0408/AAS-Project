package com.capstone.adproject.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.capstone.adproject.model.RubricTemplate;

@Repository
public interface RubricTemplateRepository extends JpaRepository<RubricTemplate, Long> {
    List<RubricTemplate> findByCourseId(Long courseId);
}