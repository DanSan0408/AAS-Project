package com.capstone.adproject.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.LecturerGroupAssignment;

@Repository
public interface LecturerGroupAssignmentRepository extends JpaRepository<LecturerGroupAssignment, Long> {
    
    /**
     * Find all assignments for a specific assessment
     */
    List<LecturerGroupAssignment> findByAssessment(Assessment assessment);
    
    /**
     * Find all assignments for a specific group
     */
    List<LecturerGroupAssignment> findByGroup(Group group);
    
    /**
     * Find all assignments for a specific lecturer
     */
    List<LecturerGroupAssignment> findByLecturer(Lecturer lecturer);
    
    /**
     * Find assignments for a specific assessment and group
     */
    List<LecturerGroupAssignment> findByAssessmentAndGroup(Assessment assessment, Group group);
    
    /**
     * Check if an assignment exists
     */
    boolean existsByAssessmentAndGroupAndLecturer(Assessment assessment, Group group, Lecturer lecturer);
    
    
    /**
     * Delete all assignments for an assessment
     */
    @Modifying
    @Transactional
    void deleteByAssessment(Assessment assessment);
    
    /**
     * ✅ NEW: Find all assessments assigned to a lecturer
     */
    @Query("SELECT DISTINCT lga.assessment FROM LecturerGroupAssignment lga WHERE lga.lecturer = :lecturer")
    List<Assessment> findAssessmentsByLecturer(@Param("lecturer") Lecturer lecturer);
    
    /**
     * ✅ NEW: Find all groups assigned to a lecturer for a specific assessment
     */
    @Query("SELECT lga.group FROM LecturerGroupAssignment lga " +
           "WHERE lga.lecturer = :lecturer AND lga.assessment = :assessment")
    List<Group> findGroupsByLecturerAndAssessment(
        @Param("lecturer") Lecturer lecturer, 
        @Param("assessment") Assessment assessment
    );
    
    /**
     * Find assignments by lecturer and assessment
     */
    @Query("SELECT lga FROM LecturerGroupAssignment lga " +
           "WHERE lga.lecturer = :lecturer AND lga.assessment = :assessment")
    List<LecturerGroupAssignment> findByLecturerAndAssessment(
        @Param("lecturer") Lecturer lecturer,
        @Param("assessment") Assessment assessment
    );
    
}