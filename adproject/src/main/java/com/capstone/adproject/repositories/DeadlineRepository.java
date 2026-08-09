package com.capstone.adproject.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.capstone.adproject.model.Deadline;

@Repository
public interface DeadlineRepository extends JpaRepository<Deadline, Long> {
    
    Optional<Deadline> findByTitle(String title);

    @Query(value = """
        SELECT d.*
        FROM deadlines d
        LEFT JOIN assessment a ON d.assessmentId = a.id
        WHERE d.courseId = :courseId
           OR (d.courseId IS NULL AND a.course_id = :courseId)
        """, nativeQuery = true)
    List<Deadline> findByCourseId(@Param("courseId") Long courseId);
    
    List<Deadline> findByAssessorType(String assessorType);
    
    List<Deadline> findByAssessmentId(Long assessmentId);
    
    List<Deadline> findByAssessmentIdAndAssessorType(Long assessmentId, String assessorType);
}