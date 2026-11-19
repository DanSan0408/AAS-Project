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
    List<Lecturer> findAllByEmailIgnoreCase(String email);
    List<Lecturer> findAllByUsernameIgnoreCase(String username);

    Optional<Lecturer> findByResetPasswordToken(String resetPasswordToken);

    List<Lecturer> findByEmailContainingIgnoreCase(String email);
    List<Lecturer> findByCourseIdAndEmailContainingIgnoreCase(Long courseId, String email);
    Optional<Lecturer> findByCourseIdAndEmailIgnoreCase(Long courseId, String email);
    Optional<Lecturer> findByCourseIdAndUsernameIgnoreCase(Long courseId, String username);
    Optional<Lecturer> findByCourseIdAndId(Long courseId, Long id);

    List<Lecturer> findByRolesContaining(String role);

    List<Lecturer> findByCourseId(Long courseId);
    long countByCourseId(Long courseId);

    @org.springframework.transaction.annotation.Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Lecturer l SET l.welcomeEmailSentAt = :sentAt WHERE LOWER(l.email) = LOWER(:email)")
    int markWelcomeEmailSent(@Param("email") String email, @Param("sentAt") java.time.LocalDateTime sentAt);

    @Modifying
    @Query("UPDATE Group g SET g.academicSupervisor = null WHERE g.academicSupervisor.id = :lecturerId")
    void unlinkFromGroups(@Param("lecturerId") Long lecturerId);

    @Modifying
    @Query("DELETE FROM LecturerGroupAssignment lga WHERE lga.lecturer.id = :lecturerId")
    void deleteGroupAssignments(@Param("lecturerId") Long lecturerId);

    @Modifying
    @Query("DELETE FROM LecturerStudentAssignment lsa WHERE lsa.lecturer.id = :lecturerId")
    void deleteStudentAssignments(@Param("lecturerId") Long lecturerId);

    @Modifying
    @Query("DELETE FROM Mark m WHERE m.lecturer.id = :lecturerId")
    void deleteMarksGiven(@Param("lecturerId") Long lecturerId);

    @Modifying
    @Query("DELETE FROM AssessmentComment ac WHERE ac.evaluatorId = :lecturerId AND ac.evaluatorType = 'LECTURER'")
    void deleteCommentsByLecturer(@Param("lecturerId") Long lecturerId);
}
