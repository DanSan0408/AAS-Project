package com.capstone.adproject.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
     * Find all assignments for a specific lecturer
     */
    List<LecturerGroupAssignment> findByLecturer(Lecturer lecturer);
    
    /**
     * Find all assignments for a specific group
     */
    List<LecturerGroupAssignment> findByGroup(Group group);
    
    /**
     * Find assignments for a lecturer and assessment
     */
    List<LecturerGroupAssignment> findByLecturerAndAssessment(Lecturer lecturer, Assessment assessment);
    
    /**
     * Find assignments for a group and assessment
     */
    List<LecturerGroupAssignment> findByGroupAndAssessment(Group group, Assessment assessment);
    
    /**
     * Check if a specific assignment exists
     */
    boolean existsByAssessmentAndGroupAndLecturer(Assessment assessment, Group group, Lecturer lecturer);
    
    /**
     * Delete all assignments for an assessment
     */
    void deleteByAssessment(Assessment assessment);
    
    /**
     * Get all groups assigned to a lecturer for a specific assessment
     */
    @Query("SELECT DISTINCT lga.group FROM LecturerGroupAssignment lga WHERE lga.lecturer = :lecturer AND lga.assessment = :assessment")
    List<Group> findGroupsByLecturerAndAssessment(@Param("lecturer") Lecturer lecturer, @Param("assessment") Assessment assessment);
    
    /**
     * Get all lecturers assigned to a group for a specific assessment
     */
    @Query("SELECT DISTINCT lga.lecturer FROM LecturerGroupAssignment lga WHERE lga.group = :group AND lga.assessment = :assessment")
    List<Lecturer> findLecturersByGroupAndAssessment(@Param("group") Group group, @Param("assessment") Assessment assessment);
    
    /**
     * Get all assessments where a lecturer has assignments
     */
    @Query("SELECT DISTINCT lga.assessment FROM LecturerGroupAssignment lga WHERE lga.lecturer = :lecturer")
    List<Assessment> findAssessmentsByLecturer(@Param("lecturer") Lecturer lecturer);
}