package com.capstone.adproject.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Mark;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.SubRubric;

@Repository
public interface MarkRepository extends JpaRepository<Mark, Long> {
    
    // Find all marks for a specific evaluator and assessment
    List<Mark> findByEvaluatorStudentAndAssessment(Student evaluator, Assessment assessment);
    
    // Find all marks given by an evaluator for a specific evaluated student and assessment
    List<Mark> findByEvaluatorStudentAndEvaluatedStudentAndAssessment(
            Student evaluator, Student evaluatedStudent, Assessment assessment);
    
    // Find a specific mark for a rubric
    Optional<Mark> findByEvaluatorStudentAndEvaluatedStudentAndAssessmentAndRubric(
            Student evaluator, Student evaluatedStudent, Assessment assessment, Rubric rubric);
    
    // Find all marks for a group in an assessment
    @Query("SELECT m FROM Mark m WHERE m.evaluatorStudent IN :groupMembers AND m.assessment = :assessment")
    List<Mark> findByGroupMembersAndAssessment(@Param("groupMembers") List<Student> groupMembers, 
                                               @Param("assessment") Assessment assessment);
    
    List<Mark> findByEvaluatedStudentAndAssessmentAndAssessmentType(
        Student evaluatedStudent, Assessment assessment, String assessmentType);
    
    // Count submitted assessments for a student
    long countByEvaluatorStudentAndAssessmentAndStatus(
            Student evaluator, Assessment assessment, Mark.SubmissionStatus status);
    
    // Find all marks by status
    List<Mark> findByEvaluatorStudentAndStatus(Student evaluator, Mark.SubmissionStatus status);
    
    // Check if student has completed peer assessment for all team members
    @Query("SELECT COUNT(DISTINCT m.evaluatedStudent) FROM Mark m WHERE m.evaluatorStudent = :evaluator " +
           "AND m.assessment = :assessment AND m.status = :status")
    long countDistinctEvaluatedStudentsByEvaluatorAndAssessmentAndStatus(
            @Param("evaluator") Student evaluator, 
            @Param("assessment") Assessment assessment,
            @Param("status") Mark.SubmissionStatus status);
    
    // Calculate total marks for a student in an assessment
    @Query("SELECT SUM(m.markValue) FROM Mark m WHERE m.evaluatedStudent = :student " +
           "AND m.assessment = :assessment AND m.status = 'FINAL'")
    Double calculateTotalMarksForStudent(@Param("student") Student student, 
                                        @Param("assessment") Assessment assessment);
    
    // Calculate CLO marks for a student
    @Query("SELECT m.clo, SUM(m.cloMarks) FROM Mark m WHERE m.evaluatedStudent = :student " +
           "AND m.assessment = :assessment AND m.status = 'FINAL' GROUP BY m.clo")
    List<Object[]> calculateCloMarksForStudent(@Param("student") Student student, 
                                              @Param("assessment") Assessment assessment);
    
    // Delete all draft marks for a student
    void deleteByEvaluatorStudentAndStatus(Student evaluator, Mark.SubmissionStatus status);
    
    // Find all marks by assessment and status
    List<Mark> findByAssessmentAndStatus(Assessment assessment, Mark.SubmissionStatus status);

    List<Mark> findByEvaluatorStudentAndAssessmentAndStatus(
        Student evaluator, Assessment assessment, Mark.SubmissionStatus status);

    List<Mark> findByEvaluatedStudentAndAssessment(
        Student evaluatedStudent, 
        Assessment assessment
    );
    
    // Find all marks for a specific rubric (for cascade deletion)
    List<Mark> findByRubric(Rubric rubric);
    
    // Find all marks for a specific sub-rubric (for cascade deletion)
    List<Mark> findBySubRubric(SubRubric subRubric);

    List<Mark> findByEvaluatedStudent(Student student);
    
    // ========== INDUSTRIAL SUPERVISOR EVALUATION METHODS ==========
    
    /**
     * Count marks by supervisor ID and assessment (for progress tracking)
     */
    @Query("SELECT COUNT(m) FROM Mark m WHERE m.supervisorId = :supervisorId " +
           "AND m.assessment.id = :assessmentId")
    Long countBySupervisorIdAndAssessmentId(
            @Param("supervisorId") Long supervisorId,
            @Param("assessmentId") Long assessmentId);
    
    /**
     * Find marks by evaluator student ID, evaluated student ID and assessment ID
     */
    @Query("SELECT m FROM Mark m WHERE m.evaluatorStudent.id = :evaluatorId " +
           "AND m.evaluatedStudent.id = :evaluatedId AND m.assessment.id = :assessmentId")
    List<Mark> findByEvaluatorStudentIdAndEvaluatedStudentIdAndAssessmentId(
            @Param("evaluatorId") Long evaluatorId,
            @Param("evaluatedId") Long evaluatedId,
            @Param("assessmentId") Long assessmentId);

            // In MarkRepository.java
List<Mark> findByEvaluatorStudentAndEvaluatedStudent(Student evaluatorStudent, Student evaluatedStudent);

List<Mark> findByAssessment(Assessment assessment);

List<Mark> findByEvaluatedStudentAndAssessmentAndRubric(Student evaluatedStudent, Assessment assessment, Rubric rubric);
}