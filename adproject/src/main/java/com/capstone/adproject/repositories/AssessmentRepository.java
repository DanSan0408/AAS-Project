    package com.capstone.adproject.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.model.Assessment;

@Repository
public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

    List<Assessment> findByTitle(String title);

    @Query("SELECT DISTINCT a FROM Assessment a LEFT JOIN FETCH a.rubrics WHERE a.course.id = :courseId")
    List<Assessment> findAllWithRubricsByCourseId(@Param("courseId") Long courseId);
    
    @Query("SELECT DISTINCT a FROM Assessment a " +
          "LEFT JOIN FETCH a.rubrics r " +
          "LEFT JOIN FETCH r.subRubrics sr " +
          "LEFT JOIN FETCH sr.ratings rat " +
          "WHERE a.id = :id")
    Optional<Assessment> findByIdWithFullRubricDetails(@Param("id") Long id);
    
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM calculated_results WHERE assessment_id = :assessmentId", nativeQuery = true)
    void deleteCalculatedResultsByAssessmentId(@Param("assessmentId") Long assessmentId);
    
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM assessment_comments WHERE assessment_id = :assessmentId", nativeQuery = true)
    void deleteCommentsByAssessmentId(@Param("assessmentId") Long assessmentId);
    
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM lecturer_group_assignment WHERE assessment_id = :assessmentId", nativeQuery = true)
    void deleteLecturerAssignmentsByAssessmentId(@Param("assessmentId") Long assessmentId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM lecturer_student_assignment WHERE assessment_id = :assessmentId", nativeQuery = true)
    void deleteLecturerStudentAssignmentsByAssessmentId(@Param("assessmentId") Long assessmentId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM student_assessment_assignment WHERE assessment_id = :assessmentId", nativeQuery = true)
    void deleteStudentAssignmentsByAssessmentId(@Param("assessmentId") Long assessmentId);
    
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM deadlines WHERE assessmentId = :assessmentId", nativeQuery = true)
    void deleteDeadlinesByAssessmentId(@Param("assessmentId") Long assessmentId);
}