package com.capstone.adproject.repositories;

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

    Optional<Lecturer> findByEmail(String email);

    // **New Method for Forgot Password - Find by Token**
    Optional<Lecturer> findByResetPasswordToken(String resetPasswordToken);

    // 1. Unassign as Academic Supervisor from Groups (Set to NULL, don't delete Group)
    @Modifying
    @Query("UPDATE Group g SET g.academicSupervisor = null WHERE g.academicSupervisor.id = :lecturerId")
    void unlinkFromGroups(@Param("lecturerId") Long lecturerId);

    // 2. Delete assignments to assess groups
    @Modifying
    @Query("DELETE FROM LecturerGroupAssignment lga WHERE lga.lecturer.id = :lecturerId")
    void deleteGroupAssignments(@Param("lecturerId") Long lecturerId);

    // 3. Delete marks given by this lecturer
    @Modifying
    @Query("DELETE FROM Mark m WHERE m.lecturer.id = :lecturerId")
    void deleteMarksGiven(@Param("lecturerId") Long lecturerId);
}
