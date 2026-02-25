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

    List<Student> findByGroupId(Long groupId);

    List<Student> findByGroup(Group group);

    long countByGroup(Group group);

    List<Student> findByGroupIsNullAndUsernameContainingIgnoreCase(String username);

    long countByGroupIsNull();

    Optional<Student> findByEmail(String email);

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
    @Query("DELETE FROM StudentResultOverride o WHERE o.student.id = :studentId")
    void deleteOverridesByStudent(@Param("studentId") Long studentId);
}