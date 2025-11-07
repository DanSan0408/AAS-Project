package com.capstone.adproject.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.AssessmentComment;
import com.capstone.adproject.model.Student;

@Repository
public interface AssessmentCommentRepository extends JpaRepository<AssessmentComment, Long> {
    
    /**
     * Find all comments for a specific student (all assessments)
     */
    List<AssessmentComment> findByEvaluatedStudent(Student student);
    
    /**
     * Find all comments for a student in a specific assessment
     */
    List<AssessmentComment> findByEvaluatedStudentAndAssessment(Student student, Assessment assessment);
    
    /**
     * Find comments given by a specific evaluator for a student in an assessment
     */
    @Query("SELECT c FROM AssessmentComment c WHERE c.evaluatorId = :evaluatorId " +
           "AND c.evaluatorType = :evaluatorType " +
           "AND c.evaluatedStudent = :student " +
           "AND c.assessment = :assessment")
    Optional<AssessmentComment> findByEvaluatorAndStudentAndAssessment(
            @Param("evaluatorId") Long evaluatorId,
            @Param("evaluatorType") AssessmentComment.EvaluatorType evaluatorType,
            @Param("student") Student student,
            @Param("assessment") Assessment assessment);
    
    /**
     * Get all comments for a student, ordered by assessment and submission date
     */
    @Query("SELECT c FROM AssessmentComment c WHERE c.evaluatedStudent = :student " +
           "ORDER BY c.assessment.id, c.submittedAt DESC")
    List<AssessmentComment> findAllCommentsForStudent(@Param("student") Student student);
    
    /**
     * Get peer comments for a student in an assessment (excluding self)
     */
    @Query("SELECT c FROM AssessmentComment c WHERE c.evaluatedStudent = :student " +
           "AND c.assessment = :assessment " +
           "AND c.evaluatorType = 'STUDENT' " +
           "AND c.assessmentType = 'PEER' " +
           "ORDER BY c.submittedAt")
    List<AssessmentComment> findPeerCommentsForStudentInAssessment(
            @Param("student") Student student,
            @Param("assessment") Assessment assessment);
    
    /**
     * Get self comment for a student in an assessment
     */
    @Query("SELECT c FROM AssessmentComment c WHERE c.evaluatedStudent = :student " +
           "AND c.assessment = :assessment " +
           "AND c.evaluatorId = :studentId " +
           "AND c.assessmentType = 'SELF'")
    Optional<AssessmentComment> findSelfCommentForStudentInAssessment(
            @Param("student") Student student,
            @Param("assessment") Assessment assessment,
            @Param("studentId") Long studentId);
    
    /**
     * Get lecturer comments for a student
     */
    @Query("SELECT c FROM AssessmentComment c WHERE c.evaluatedStudent = :student " +
           "AND c.evaluatorType = 'LECTURER' " +
           "ORDER BY c.assessment.id, c.submittedAt DESC")
    List<AssessmentComment> findLecturerCommentsForStudent(@Param("student") Student student);
    
    /**
     * Get supervisor comments for a student
     */
    @Query("SELECT c FROM AssessmentComment c WHERE c.evaluatedStudent = :student " +
           "AND c.evaluatorType = 'SUPERVISOR' " +
           "ORDER BY c.assessment.id, c.submittedAt DESC")
    List<AssessmentComment> findSupervisorCommentsForStudent(@Param("student") Student student);
    
    /**
     * Count comments submitted by an evaluator in an assessment
     */
    @Query("SELECT COUNT(c) FROM AssessmentComment c WHERE c.evaluatorId = :evaluatorId " +
           "AND c.evaluatorType = :evaluatorType " +
           "AND c.assessment = :assessment")
    long countByEvaluatorAndAssessment(
            @Param("evaluatorId") Long evaluatorId,
            @Param("evaluatorType") AssessmentComment.EvaluatorType evaluatorType,
            @Param("assessment") Assessment assessment);
}