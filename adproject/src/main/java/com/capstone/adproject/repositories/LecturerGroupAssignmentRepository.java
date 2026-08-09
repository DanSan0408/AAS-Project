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
    
    List<LecturerGroupAssignment> findByAssessment(Assessment assessment);
    
    List<LecturerGroupAssignment> findByGroup(Group group);
    
    List<LecturerGroupAssignment> findByLecturer(Lecturer lecturer);
    
    List<LecturerGroupAssignment> findByAssessmentAndGroup(Assessment assessment, Group group);
    
    boolean existsByAssessmentAndGroupAndLecturer(Assessment assessment, Group group, Lecturer lecturer);
    
    @Modifying
    @Transactional
    void deleteByAssessment(Assessment assessment);
    
    @Query("SELECT DISTINCT lga.assessment FROM LecturerGroupAssignment lga WHERE lga.lecturer = :lecturer")
    List<Assessment> findAssessmentsByLecturer(@Param("lecturer") Lecturer lecturer);
    
    @Query("SELECT lga.group FROM LecturerGroupAssignment lga " +
           "WHERE lga.lecturer = :lecturer AND lga.assessment = :assessment")
    List<Group> findGroupsByLecturerAndAssessment(
        @Param("lecturer") Lecturer lecturer, 
        @Param("assessment") Assessment assessment
    );
    
    @Query("SELECT lga FROM LecturerGroupAssignment lga " +
           "WHERE lga.lecturer = :lecturer AND lga.assessment = :assessment")
    List<LecturerGroupAssignment> findByLecturerAndAssessment(
        @Param("lecturer") Lecturer lecturer,
        @Param("assessment") Assessment assessment
    );
    
    @Modifying
    @Query("DELETE FROM LecturerGroupAssignment lga WHERE lga.group = :group")
    void deleteByGroup(@Param("group") Group group);
}