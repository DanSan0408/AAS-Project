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
    List<AssessmentComment> findByEvaluatorAndStudentAndAssessment(
            @Param("evaluatorId") Long evaluatorId,
            @Param("evaluatorType") AssessmentComment.EvaluatorType evaluatorType,
            @Param("student") Student student,
            @Param("assessment") Assessment assessment);
    
    /**
     * Find a specific comment by evaluator, student, assessment, and comment index
     */
    @Query("SELECT c FROM AssessmentComment c WHERE c.evaluatorId = :evaluatorId " +
           "AND c.evaluatorType = :evaluatorType " +
           "AND c.evaluatedStudent = :student " +
           "AND c.assessment = :assessment " +
           "AND c.commentIndex = :commentIndex")
    Optional<AssessmentComment> findByEvaluatorAndStudentAndAssessmentAndIndex(
            @Param("evaluatorId") Long evaluatorId,
            @Param("evaluatorType") AssessmentComment.EvaluatorType evaluatorType,
            @Param("student") Student student,
            @Param("assessment") Assessment assessment,
            @Param("commentIndex") Integer commentIndex);
    
    /**
     * Get all comments for a student, ordered by assessment and submission date
     */
    @Query("SELECT c FROM AssessmentComment c WHERE c.evaluatedStudent = :student " +
           "ORDER BY c.assessment.id, c.commentIndex, c.submittedAt DESC")
    List<AssessmentComment> findAllCommentsForStudent(@Param("student") Student student);
    
    /**
     * Get peer comments for a student in an assessment (excluding self)
     */
    @Query("SELECT c FROM AssessmentComment c WHERE c.evaluatedStudent = :student " +
           "AND c.assessment = :assessment " +
           "AND c.evaluatorType = 'STUDENT' " +
           "AND c.assessmentType = 'PEER' " +
           "ORDER BY c.commentIndex, c.submittedAt")
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
    List<AssessmentComment> findSelfCommentsForStudentInAssessment(
            @Param("student") Student student,
            @Param("assessment") Assessment assessment,
            @Param("studentId") Long studentId);
    
    /**
     * Get lecturer comments for a student
     */
    @Query("SELECT c FROM AssessmentComment c WHERE c.evaluatedStudent = :student " +
           "AND c.evaluatorType = 'LECTURER' " +
           "ORDER BY c.assessment.id, c.commentIndex, c.submittedAt DESC")
    List<AssessmentComment> findLecturerCommentsForStudent(@Param("student") Student student);
    
    /**
     * Get supervisor comments for a student
     */
    @Query("SELECT c FROM AssessmentComment c WHERE c.evaluatedStudent = :student " +
           "AND c.evaluatorType = 'SUPERVISOR' " +
           "ORDER BY c.assessment.id, c.commentIndex, c.submittedAt DESC")
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
    
    /**
     * Delete all comments by evaluator for a student in an assessment
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM AssessmentComment c WHERE c.evaluatorId = :evaluatorId " +
           "AND c.evaluatorType = :evaluatorType " +
           "AND c.evaluatedStudent = :student " +
           "AND c.assessment = :assessment")
    void deleteByEvaluatorAndStudentAndAssessment(
            @Param("evaluatorId") Long evaluatorId,
            @Param("evaluatorType") AssessmentComment.EvaluatorType evaluatorType,
            @Param("student") Student student,
            @Param("assessment") Assessment assessment);
    
    /**
     * Find comments by evaluator ID and type
     * Useful for finding all comments made by a specific lecturer or supervisor
     */
    List<AssessmentComment> findByEvaluatorIdAndEvaluatorType(
        Long evaluatorId, 
        AssessmentComment.EvaluatorType evaluatorType
    );
    
    /**
     * Find comments for a student in an assessment by evaluator type
     * Useful for separating peer, self, lecturer, and supervisor comments
     */
    List<AssessmentComment> findByEvaluatedStudentAndAssessmentAndEvaluatorType(
        Student evaluatedStudent, 
        Assessment assessment,
        AssessmentComment.EvaluatorType evaluatorType
    );
    
    /**
     * Find comments by evaluated student, assessment, evaluator ID and type
     * Useful for checking if a specific evaluator has already commented
     */
    List<AssessmentComment> findByEvaluatedStudentAndAssessmentAndEvaluatorIdAndEvaluatorType(
        Student evaluatedStudent,
        Assessment assessment,
        Long evaluatorId,
        AssessmentComment.EvaluatorType evaluatorType
    );
    
    /**
     * Find all comments for an assessment
     * Useful for admin review
     */
    List<AssessmentComment> findByAssessment(Assessment assessment);
    
    /**
     * Find comments by assessment and evaluator type
     * Useful for analyzing comment patterns by evaluator type
     */
    List<AssessmentComment> findByAssessmentAndEvaluatorType(
        Assessment assessment,
        AssessmentComment.EvaluatorType evaluatorType
    );
    
    // ========== INDUSTRIAL SUPERVISOR EVALUATION METHODS ==========
    
    /**
     * Find supervisor comments for a specific student in an assessment
     */
    @Query("SELECT c FROM AssessmentComment c WHERE c.evaluatorId = :supervisorId " +
           "AND c.evaluatedStudent.id = :studentId " +
           "AND c.assessment.id = :assessmentId " +
           "AND c.evaluatorType = 'SUPERVISOR' " +
           "ORDER BY c.commentIndex")
    List<AssessmentComment> findSupervisorCommentsForStudent(
            @Param("supervisorId") Long supervisorId,
            @Param("studentId") Long studentId,
            @Param("assessmentId") Long assessmentId);
    
    /**
     * Delete supervisor comments for a student with specific assessment type (for re-evaluation)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM AssessmentComment c WHERE c.evaluatorId = :supervisorId " +
           "AND c.evaluatedStudent.id = :studentId " +
           "AND c.assessment.id = :assessmentId " +
           "AND c.evaluatorType = 'SUPERVISOR' " +
           "AND c.rubricAssessmentType = :rubricAssessmentType")
    void deleteSupervisorCommentsForStudent(
            @Param("supervisorId") Long supervisorId,
            @Param("studentId") Long studentId,
            @Param("assessmentId") Long assessmentId,
            @Param("rubricAssessmentType") String rubricAssessmentType);
}