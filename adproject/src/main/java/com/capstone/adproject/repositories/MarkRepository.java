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
    
    List<Mark> findByEvaluatorStudentAndAssessment(Student evaluator, Assessment assessment);
    
    List<Mark> findByEvaluatorStudentAndEvaluatedStudentAndAssessment(
            Student evaluator, Student evaluatedStudent, Assessment assessment);
    
    Optional<Mark> findByEvaluatorStudentAndEvaluatedStudentAndAssessmentAndRubric(
            Student evaluator, Student evaluatedStudent, Assessment assessment, Rubric rubric);
    
    @Query("SELECT m FROM Mark m WHERE m.evaluatorStudent IN :groupMembers AND m.assessment = :assessment")
    List<Mark> findByGroupMembersAndAssessment(@Param("groupMembers") List<Student> groupMembers, 
                                               @Param("assessment") Assessment assessment);
    
    List<Mark> findByEvaluatedStudentAndAssessmentAndAssessmentType(
        Student evaluatedStudent, Assessment assessment, String assessmentType);
    
    long countByEvaluatorStudentAndAssessmentAndStatus(
            Student evaluator, Assessment assessment, Mark.SubmissionStatus status);
    
    List<Mark> findByEvaluatorStudentAndStatus(Student evaluator, Mark.SubmissionStatus status);
    
    @Query("SELECT COUNT(DISTINCT m.evaluatedStudent) FROM Mark m WHERE m.evaluatorStudent = :evaluator " +
           "AND m.assessment = :assessment AND m.status = :status")
    long countDistinctEvaluatedStudentsByEvaluatorAndAssessmentAndStatus(
            @Param("evaluator") Student evaluator, 
            @Param("assessment") Assessment assessment,
            @Param("status") Mark.SubmissionStatus status);
    
    @Query("SELECT SUM(m.markValue) FROM Mark m WHERE m.evaluatedStudent = :student " +
           "AND m.assessment = :assessment AND m.status = 'FINAL'")
    Double calculateTotalMarksForStudent(@Param("student") Student student, 
                                        @Param("assessment") Assessment assessment);
    
    @Query("SELECT m.clo, SUM(m.cloMarks) FROM Mark m WHERE m.evaluatedStudent = :student " +
           "AND m.assessment = :assessment AND m.status = 'FINAL' GROUP BY m.clo")
    List<Object[]> calculateCloMarksForStudent(@Param("student") Student student, 
                                              @Param("assessment") Assessment assessment);
    
    void deleteByEvaluatorStudentAndStatus(Student evaluator, Mark.SubmissionStatus status);
    
    List<Mark> findByAssessmentAndStatus(Assessment assessment, Mark.SubmissionStatus status);

    List<Mark> findByEvaluatorStudentAndAssessmentAndStatus(
        Student evaluator, Assessment assessment, Mark.SubmissionStatus status);

    List<Mark> findByEvaluatedStudentAndAssessment(
        Student evaluatedStudent, 
        Assessment assessment
    );
    
    List<Mark> findByRubric(Rubric rubric);
    
    List<Mark> findBySubRubric(SubRubric subRubric);

    List<Mark> findByEvaluatedStudent(Student student);
    
    @Query("SELECT COUNT(m) FROM Mark m WHERE m.supervisorId = :supervisorId " +
           "AND m.assessment.id = :assessmentId")
    Long countBySupervisorIdAndAssessmentId(
            @Param("supervisorId") Long supervisorId,
            @Param("assessmentId") Long assessmentId);
    
    @Query("SELECT m FROM Mark m WHERE m.evaluatorStudent.id = :evaluatorId " +
           "AND m.evaluatedStudent.id = :evaluatedId AND m.assessment.id = :assessmentId")
    List<Mark> findByEvaluatorStudentIdAndEvaluatedStudentIdAndAssessmentId(
            @Param("evaluatorId") Long evaluatorId,
            @Param("evaluatedId") Long evaluatedId,
            @Param("assessmentId") Long assessmentId);

List<Mark> findByEvaluatorStudentAndEvaluatedStudent(Student evaluatorStudent, Student evaluatedStudent);

List<Mark> findByAssessment(Assessment assessment);

List<Mark> findByEvaluatedStudentAndAssessmentAndRubric(Student evaluatedStudent, Assessment assessment, Rubric rubric);
}