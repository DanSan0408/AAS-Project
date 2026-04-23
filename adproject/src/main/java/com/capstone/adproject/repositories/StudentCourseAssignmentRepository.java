package com.capstone.adproject.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.StudentCourseAssignment;

@Repository
public interface StudentCourseAssignmentRepository extends JpaRepository<StudentCourseAssignment, Long> {

    boolean existsByStudentIdAndCourseId(Long studentId, Long courseId);

    List<StudentCourseAssignment> findByStudentId(Long studentId);

    List<StudentCourseAssignment> findByCourseId(Long courseId);

    long countByStudentId(Long studentId);

    @Modifying
    void deleteByStudentIdAndCourseId(Long studentId, Long courseId);

    @Modifying
    void deleteByStudentId(Long studentId);

    @Query("""
        SELECT s
        FROM StudentCourseAssignment sca
        JOIN sca.student s
        LEFT JOIN FETCH s.group
        WHERE sca.course.id = :courseId
        """)
    List<Student> findStudentsByCourseId(@Param("courseId") Long courseId);

    @Query("""
        SELECT s
        FROM StudentCourseAssignment sca
        JOIN sca.student s
        LEFT JOIN FETCH s.group
        WHERE sca.course.id = :courseId AND s.group IS NULL
        """)
    List<Student> findStudentsWithoutGroupByCourseId(@Param("courseId") Long courseId);

    @Query("""
        SELECT COUNT(s)
        FROM StudentCourseAssignment sca
        JOIN sca.student s
        WHERE sca.course.id = :courseId AND s.group IS NULL
        """)
    long countStudentsWithoutGroupByCourseId(@Param("courseId") Long courseId);

    @Query("""
        SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
        FROM StudentCourseAssignment sca
        JOIN sca.student s
        WHERE sca.course.id = :courseId AND LOWER(s.email) = LOWER(:email)
        """)
    boolean existsByCourseIdAndEmailIgnoreCase(@Param("courseId") Long courseId, @Param("email") String email);
}
