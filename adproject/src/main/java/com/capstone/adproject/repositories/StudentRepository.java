package com.capstone.adproject.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Student;

public interface StudentRepository extends JpaRepository<Student, Long> {
    
    Optional<Student> findByUsername(String username);

    List<Student> findByGroupIsNull();

    @Query("SELECT s FROM Student s LEFT JOIN FETCH s.group")
    List<Student> findAllWithGroupEagerly();

    @Query("SELECT s FROM Student s LEFT JOIN FETCH s.group WHERE s.course.id = :courseId")
    List<Student> findAllWithGroupEagerlyByCourseId(@Param("courseId") Long courseId);

    @Query("SELECT DISTINCT s FROM Student s LEFT JOIN FETCH s.group g LEFT JOIN FETCH g.course WHERE g.course.id = :courseId")
    List<Student> findByGroupCourseIdWithGroupFetched(@Param("courseId") Long courseId);

    @Query(value = "SELECT s.* FROM student s JOIN project_group g ON g.id = s.group_id WHERE g.course_id = :courseId", nativeQuery = true)
    List<Student> findByGroupCourseIdNative(@Param("courseId") Long courseId);

    List<Student> findByCourseIdAndGroupIsNull(Long courseId);

    List<Student> findByGroupId(Long groupId);

    List<Student> findByGroupIdIn(List<Long> groupIds);

    List<Student> findByGroup(Group group);

    List<Student> findByCourseId(Long courseId);

    long countByGroup(Group group);

    List<Student> findByGroupIsNullAndUsernameContainingIgnoreCase(String username);

    long countByGroupIsNull();

    long countByCourseIdAndGroupIsNull(Long courseId);

    Optional<Student> findByEmail(String email);

    Optional<Student> findFirstByEmailIgnoreCaseOrderByIdAsc(String email);

    // **New Method for Forgot Password - Find by Token**
    Optional<Student> findByResetPasswordToken(String resetPasswordToken);

    @Modifying
    @Query(value = "DELETE FROM calculated_results WHERE student_id = :studentId", nativeQuery = true)
    void deleteCalculatedResultsByStudentId(@Param("studentId") Long studentId);

    @Modifying
    @Query("DELETE FROM AssessmentComment ac WHERE ac.evaluatedStudent.id = :studentId")
    void deleteCommentsByStudentId(@Param("studentId") Long studentId);

    @Modifying
    @Query("DELETE FROM Mark m WHERE m.evaluatedStudent.id = :studentId")
    void deleteMarksReceivedByStudent(@Param("studentId") Long studentId);

    @Modifying
    @Query("DELETE FROM Mark m WHERE m.evaluatorStudent.id = :studentId")
    void deleteMarksGivenByStudent(@Param("studentId") Long studentId);

    @Modifying
    @Query("DELETE FROM LecturerStudentAssignment lsa WHERE lsa.student.id = :studentId")
    void deleteLecturerAssignmentsByStudentId(@Param("studentId") Long studentId);

    @Modifying
    @Query("DELETE FROM StudentAssessmentAssignment saa WHERE saa.student.id = :studentId")
    void deleteAssessmentAssignmentsByStudentId(@Param("studentId") Long studentId);
    
    @Modifying
    @Query("DELETE FROM StudentResultOverride o WHERE o.student.id = :studentId")
    void deleteOverridesByStudent(@Param("studentId") Long studentId);

    @Modifying
    @Query(value = "UPDATE student SET course_id = NULL WHERE id = :studentId", nativeQuery = true)
    void clearCourseByStudentId(@Param("studentId") Long studentId);
}