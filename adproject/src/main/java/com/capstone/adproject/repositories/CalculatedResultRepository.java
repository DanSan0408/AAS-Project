package com.capstone.adproject.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.CalculatedResult;
import com.capstone.adproject.model.Student;

@Repository
public interface CalculatedResultRepository extends JpaRepository<CalculatedResult, Long> {
    
    /**
     * Find all results for a student in an assessment
     */
    List<CalculatedResult> findByStudentAndAssessment(Student student, Assessment assessment);
    
    /**
     * Find all results for an assessment
     */
    List<CalculatedResult> findByAssessment(Assessment assessment);
    
    /**
     * Find summary result (with totals) for a student in an assessment
     */
    @Query("SELECT cr FROM CalculatedResult cr WHERE cr.student = :student " +
           "AND cr.assessment = :assessment AND cr.rubric IS NULL")
    Optional<CalculatedResult> findSummaryByStudentAndAssessment(
        @Param("student") Student student, 
        @Param("assessment") Assessment assessment
    );
    
    /**
     * Delete all results for an assessment
     */
    void deleteByAssessment(Assessment assessment);
    
    /**
     * Delete all results for a student
     */
    void deleteByStudent(Student student);
}