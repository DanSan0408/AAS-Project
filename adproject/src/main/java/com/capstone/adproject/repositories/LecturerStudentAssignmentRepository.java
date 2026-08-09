package com.capstone.adproject.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.LecturerStudentAssignment;
import com.capstone.adproject.model.Student;

@Repository
public interface LecturerStudentAssignmentRepository extends JpaRepository<LecturerStudentAssignment, Long> {

    List<LecturerStudentAssignment> findByAssessment(Assessment assessment);

    List<LecturerStudentAssignment> findByStudent(Student student);

    List<LecturerStudentAssignment> findByLecturer(Lecturer lecturer);

    boolean existsByAssessmentAndStudentAndLecturer(Assessment assessment, Student student, Lecturer lecturer);

    @Modifying
    @Transactional
    void deleteByAssessment(Assessment assessment);

    @Query("SELECT DISTINCT lsa.assessment FROM LecturerStudentAssignment lsa WHERE lsa.lecturer = :lecturer")
    List<Assessment> findAssessmentsByLecturer(@Param("lecturer") Lecturer lecturer);

        @Query("SELECT DISTINCT s FROM LecturerStudentAssignment lsa " +
            "JOIN lsa.student s LEFT JOIN FETCH s.group " +
            "WHERE lsa.lecturer = :lecturer AND lsa.assessment = :assessment")
    List<Student> findStudentsByLecturerAndAssessment(
        @Param("lecturer") Lecturer lecturer,
        @Param("assessment") Assessment assessment
    );

    @Query("SELECT lsa FROM LecturerStudentAssignment lsa " +
           "WHERE lsa.lecturer = :lecturer AND lsa.assessment = :assessment")
    List<LecturerStudentAssignment> findByLecturerAndAssessment(
        @Param("lecturer") Lecturer lecturer,
        @Param("assessment") Assessment assessment
    );
}