package com.capstone.adproject.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.capstone.adproject.model.Assessment;

@Repository
public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

    List<Assessment> findByTitle(String title);


    @Query("SELECT DISTINCT a FROM Assessment a " +
          "LEFT JOIN FETCH a.rubrics r " +
          "LEFT JOIN FETCH r.subRubrics sr " +
          "LEFT JOIN FETCH sr.ratings rat " +
          "WHERE a.id = :id")
   Optional<Assessment> findByIdWithFullRubricDetails(@Param("id") Long id);
    
}