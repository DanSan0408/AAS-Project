package com.capstone.adproject.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.LecturerRubricAssignment;

@Repository
public interface LecturerRubricAssignmentRepository extends JpaRepository<LecturerRubricAssignment, Long> {
    List<LecturerRubricAssignment> findByAssessment(Assessment assessment);
    void deleteByAssessment(Assessment assessment);
    boolean existsByLecturerAndAssessment(Lecturer lecturer, Assessment assessment);
    List<LecturerRubricAssignment> findByLecturerAndAssessment(Lecturer lecturer, Assessment assessment);
    @Query("SELECT DISTINCT a.assessment FROM LecturerRubricAssignment a WHERE a.lecturer = :lecturer")
    List<Assessment> findAssessmentsByLecturer(@Param("lecturer") Lecturer lecturer);
}