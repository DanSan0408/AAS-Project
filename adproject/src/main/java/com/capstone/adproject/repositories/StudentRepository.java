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

    // New method to find students not assigned to a group (group is NULL)
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

    // --- CLEANUP METHODS FOR SAFE DELETION ---
    
    // 1. Delete calculated results (ghost table)
    @Modifying
    @Query(value = "DELETE FROM calculated_results WHERE student_id = :studentId", nativeQuery = true)
    void deleteCalculatedResultsByStudentId(@Param("studentId") Long studentId);

    // 2. ✅ NEW: Delete assessment comments where student is evaluated
    @Modifying
    @Query("DELETE FROM AssessmentComment ac WHERE ac.evaluatedStudent.id = :studentId")
    void deleteCommentsByStudentId(@Param("studentId") Long studentId);

    // 3. Delete marks received by student
    @Modifying
    @Query("DELETE FROM Mark m WHERE m.evaluatedStudent.id = :studentId")
    void deleteMarksReceivedByStudent(@Param("studentId") Long studentId);

    // 4. Delete marks given by student (peer evaluations)
    @Modifying
    @Query("DELETE FROM Mark m WHERE m.evaluatorStudent.id = :studentId")
    void deleteMarksGivenByStudent(@Param("studentId") Long studentId);
    
    // 5. Delete overrides for student
    @Modifying
    @Query("DELETE FROM StudentResultOverride o WHERE o.student.id = :studentId")
    void deleteOverridesByStudent(@Param("studentId") Long studentId);
}