package com.capstone.adproject.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.capstone.adproject.model.AdminCourseAssignment;

@Repository
public interface AdminCourseAssignmentRepository extends JpaRepository<AdminCourseAssignment, Long> {

    @Query("""
        SELECT aca
        FROM AdminCourseAssignment aca
        JOIN FETCH aca.course c
        WHERE aca.lecturer.id = :lecturerId
        """)
    List<AdminCourseAssignment> findByLecturerId(@Param("lecturerId") Long lecturerId);

    List<AdminCourseAssignment> findByCourseId(Long courseId);

    boolean existsByLecturerIdAndCourseId(Long lecturerId, Long courseId);

    @Modifying
    @Query("DELETE FROM AdminCourseAssignment aca WHERE aca.lecturer.id = :lecturerId")
    void deleteByLecturerId(@Param("lecturerId") Long lecturerId);
}
