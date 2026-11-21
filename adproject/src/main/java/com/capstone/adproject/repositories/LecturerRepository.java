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

    /** For the "is this person already registered?" suggestions while an admin types. */
    java.util.List<Lecturer> findTop5ByEmailContainingIgnoreCaseOrUsernameContainingIgnoreCaseOrderByUsernameAsc(String email, String username);
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

    // Course-scoped variants, used when the assessor also belongs to other courses and only one is being left.
    @Modifying
    @Query("UPDATE Group g SET g.academicSupervisor = null WHERE g.academicSupervisor.id = :lecturerId AND g.course.id = :courseId")
    void unlinkFromGroupsInCourse(@Param("lecturerId") Long lecturerId, @Param("courseId") Long courseId);

    @Modifying
    @Query("DELETE FROM LecturerGroupAssignment lga WHERE lga.lecturer.id = :lecturerId AND lga.assessment.course.id = :courseId")
    void deleteGroupAssignmentsInCourse(@Param("lecturerId") Long lecturerId, @Param("courseId") Long courseId);

    @Modifying
    @Query("DELETE FROM LecturerStudentAssignment lsa WHERE lsa.lecturer.id = :lecturerId AND lsa.assessment.course.id = :courseId")
    void deleteStudentAssignmentsInCourse(@Param("lecturerId") Long lecturerId, @Param("courseId") Long courseId);

    @Modifying
    @Query("DELETE FROM LecturerRubricAssignment lra WHERE lra.lecturer.id = :lecturerId AND lra.assessment.course.id = :courseId")
    void deleteRubricAssignmentsInCourse(@Param("lecturerId") Long lecturerId, @Param("courseId") Long courseId);

    @Modifying
    @Query("DELETE FROM Mark m WHERE m.lecturer.id = :lecturerId AND m.assessment.course.id = :courseId")
    void deleteMarksGivenInCourse(@Param("lecturerId") Long lecturerId, @Param("courseId") Long courseId);

    @Modifying
    @Query("DELETE FROM AssessmentComment ac WHERE ac.evaluatorId = :lecturerId AND ac.evaluatorType = 'LECTURER' AND ac.assessment.course.id = :courseId")
    void deleteCommentsByLecturerInCourse(@Param("lecturerId") Long lecturerId, @Param("courseId") Long courseId);

    @Modifying
    @Query("DELETE FROM LecturerRubricAssignment lra WHERE lra.lecturer.id = :lecturerId")
    void deleteRubricAssignmentsForLecturer(@Param("lecturerId") Long lecturerId);
}
