package com.capstone.adproject.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.capstone.adproject.model.Lecturer;

@Repository
public interface LecturerRepository extends JpaRepository<Lecturer, Long> {
    Optional<Lecturer> findByUsername(String username);
    Optional<Lecturer> findByUsernameIgnoreCase(String username);

    Optional<Lecturer> findByEmail(String email);
    Optional<Lecturer> findFirstByEmailIgnoreCaseOrderByIdAsc(String email);
    Optional<Lecturer> findByEmailIgnoreCase(String email);

    Optional<Lecturer> findByResetPasswordToken(String resetPasswordToken);

    List<Lecturer> findByEmailContainingIgnoreCase(String email);

    List<Lecturer> findByRolesContaining(String role);

    List<Lecturer> findByCourseId(Long courseId);

    @Modifying
    @Query("UPDATE Group g SET g.academicSupervisor = null WHERE g.academicSupervisor.id = :lecturerId")
    void unlinkFromGroups(@Param("lecturerId") Long lecturerId);

    @Modifying
    @Query("DELETE FROM LecturerGroupAssignment lga WHERE lga.lecturer.id = :lecturerId")
    void deleteGroupAssignments(@Param("lecturerId") Long lecturerId);

    @Modifying
    @Query("DELETE FROM Mark m WHERE m.lecturer.id = :lecturerId")
    void deleteMarksGiven(@Param("lecturerId") Long lecturerId);

    @Modifying
    @Query("DELETE FROM AssessmentComment ac WHERE ac.evaluatorId = :lecturerId AND ac.evaluatorType = 'LECTURER'")
    void deleteCommentsByLecturer(@Param("lecturerId") Long lecturerId);
}
