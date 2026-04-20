package com.capstone.adproject.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.capstone.adproject.model.Group;

public interface GroupRepository extends JpaRepository<Group, Long> {
    
    List<Group> findByAcademicSupervisorId(Long lecturerId);
    
    Group findByGroupName(String groupName);
    
    boolean existsByGroupName(String groupName);
    
    @Query("SELECT DISTINCT g FROM Group g LEFT JOIN FETCH g.students")
    List<Group> findAllWithStudents();

    @Query("SELECT DISTINCT g FROM Group g LEFT JOIN FETCH g.students WHERE g.course.id = :courseId")
    List<Group> findAllWithStudentsByCourseId(@Param("courseId") Long courseId);

    List<Group> findByCourseId(Long courseId);
}
