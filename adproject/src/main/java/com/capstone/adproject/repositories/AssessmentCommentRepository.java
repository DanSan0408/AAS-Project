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
    
    List<AssessmentComment> findByEvaluatedStudent(Student student);
    
    List<AssessmentComment> findByEvaluatedStudentAndAssessment(Student student, Assessment assessment);
    
    @Query("SELECT c FROM AssessmentComment c WHERE c.evaluatorId = :evaluatorId " +
           "AND c.evaluatorType = :evaluatorType " +
           "AND c.evaluatedStudent = :student " +
           "AND c.assessment = :assessment")
    List<AssessmentComment> findByEvaluatorAndStudentAndAssessment(
            @Param("evaluatorId") Long evaluatorId,
            @Param("evaluatorType") AssessmentComment.EvaluatorType evaluatorType,
            @Param("student") Student student,
            @Param("assessment") Assessment assessment);
    
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
    
    @Query("SELECT c FROM AssessmentComment c WHERE c.evaluatedStudent = :student " +
           "ORDER BY c.assessment.id, c.commentIndex, c.submittedAt DESC")
    List<AssessmentComment> findAllCommentsForStudent(@Param("student") Student student);
    
    @Query("SELECT c FROM AssessmentComment c WHERE c.evaluatedStudent = :student " +
           "AND c.assessment = :assessment " +
           "AND c.evaluatorType = 'STUDENT' " +
           "AND c.assessmentType = 'PEER' " +
           "ORDER BY c.commentIndex, c.submittedAt")
    List<AssessmentComment> findPeerCommentsForStudentInAssessment(
            @Param("student") Student student,
            @Param("assessment") Assessment assessment);
    
    @Query("SELECT c FROM AssessmentComment c WHERE c.evaluatedStudent = :student " +
           "AND c.assessment = :assessment " +
           "AND c.evaluatorId = :studentId " +
           "AND c.assessmentType = 'SELF'")
    List<AssessmentComment> findSelfCommentsForStudentInAssessment(
            @Param("student") Student student,
            @Param("assessment") Assessment assessment,
            @Param("studentId") Long studentId);
    
    @Query("SELECT c FROM AssessmentComment c WHERE c.evaluatedStudent = :student " +
           "AND c.evaluatorType = 'LECTURER' " +
           "ORDER BY c.assessment.id, c.commentIndex, c.submittedAt DESC")
    List<AssessmentComment> findLecturerCommentsForStudent(@Param("student") Student student);
    
    @Query("SELECT c FROM AssessmentComment c WHERE c.evaluatedStudent = :student " +
           "AND c.evaluatorType = 'SUPERVISOR' " +
           "ORDER BY c.assessment.id, c.commentIndex, c.submittedAt DESC")
    List<AssessmentComment> findSupervisorCommentsForStudent(@Param("student") Student student);
    
    @Query("SELECT COUNT(c) FROM AssessmentComment c WHERE c.evaluatorId = :evaluatorId " +
           "AND c.evaluatorType = :evaluatorType " +
           "AND c.assessment = :assessment")
    long countByEvaluatorAndAssessment(
            @Param("evaluatorId") Long evaluatorId,
            @Param("evaluatorType") AssessmentComment.EvaluatorType evaluatorType,
            @Param("assessment") Assessment assessment);
    
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
    
    List<AssessmentComment> findByEvaluatorIdAndEvaluatorType(
        Long evaluatorId, 
        AssessmentComment.EvaluatorType evaluatorType
    );
    
    List<AssessmentComment> findByEvaluatedStudentAndAssessmentAndEvaluatorType(
        Student evaluatedStudent, 
        Assessment assessment,
        AssessmentComment.EvaluatorType evaluatorType
    );
    
    List<AssessmentComment> findByEvaluatedStudentAndAssessmentAndEvaluatorIdAndEvaluatorType(
        Student evaluatedStudent,
        Assessment assessment,
        Long evaluatorId,
        AssessmentComment.EvaluatorType evaluatorType
    );
    
    List<AssessmentComment> findByAssessment(Assessment assessment);
    
    List<AssessmentComment> findByAssessmentAndEvaluatorType(
        Assessment assessment,
        AssessmentComment.EvaluatorType evaluatorType
    );
    
    @Query("SELECT c FROM AssessmentComment c WHERE c.evaluatorId = :supervisorId " +
           "AND c.evaluatedStudent.id = :studentId " +
           "AND c.assessment.id = :assessmentId " +
           "AND c.evaluatorType = 'SUPERVISOR' " +
           "ORDER BY c.commentIndex")
    List<AssessmentComment> findSupervisorCommentsForStudent(
            @Param("supervisorId") Long supervisorId,
            @Param("studentId") Long studentId,
            @Param("assessmentId") Long assessmentId);
    
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

    List<AssessmentComment> findByEvaluatedStudentAndAssessmentAndRubricAssessmentType(
            Student evaluatedStudent, Assessment assessment, String rubricAssessmentType);
    
    List<AssessmentComment> findByRubricId(Long rubricId);
    
    @Query("SELECT ac FROM AssessmentComment ac " +
           "WHERE ac.evaluatedStudent = :student " +
           "AND ac.assessment = :assessment " +
           "AND ac.rubricId = :rubricId")
    List<AssessmentComment> findByEvaluatedStudentAndAssessmentAndRubricId(
        @Param("student") Student evaluatedStudent, 
        @Param("assessment") Assessment assessment, 
        @Param("rubricId") Long rubricId
    );
}