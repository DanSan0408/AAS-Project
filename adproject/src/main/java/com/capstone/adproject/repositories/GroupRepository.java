package com.capstone.adproject.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.capstone.adproject.model.Group;

public interface GroupRepository extends JpaRepository<Group, Long> {

    List<Group> findByIndustrialSupervisorId(Long industrialSupervisorId);
    
    /**
     * Find all groups assigned to a specific academic supervisor (lecturer)
     */
    List<Group> findByAcademicSupervisorId(Long lecturerId);
    
    /**
     * Find group by group name
     */
    Group findByGroupName(String groupName);
    
    /**
     * Check if group name exists
     */
    boolean existsByGroupName(String groupName);
    
    /**
     * Find all groups with their students loaded (fetch join to avoid N+1 problem)
     */
    @Query("SELECT DISTINCT g FROM Group g LEFT JOIN FETCH g.students")
    List<Group> findAllWithStudents();
    
    /**
     * Find groups by industrial supervisor with students loaded
     */
    @Query("SELECT DISTINCT g FROM Group g LEFT JOIN FETCH g.students WHERE g.industrialSupervisor.id = :supervisorId")
    List<Group> findByIndustrialSupervisorIdWithStudents(@Param("supervisorId") Long supervisorId);
}