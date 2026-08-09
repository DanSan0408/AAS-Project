package com.capstone.adproject.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.SubRubric;
@Repository
public interface SubRubricRepository extends JpaRepository<SubRubric, Long> {
    List<SubRubric> findByRubric(Rubric rubric);
}
