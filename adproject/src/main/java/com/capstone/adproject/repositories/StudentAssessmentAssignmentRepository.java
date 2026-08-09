package com.capstone.adproject.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.StudentAssessmentAssignment;

@Repository
public interface StudentAssessmentAssignmentRepository extends JpaRepository<StudentAssessmentAssignment, Long> {

    List<StudentAssessmentAssignment> findByAssessment(Assessment assessment);

    List<StudentAssessmentAssignment> findByStudent(Student student);

    boolean existsByAssessmentAndStudent(Assessment assessment, Student student);

    @Modifying
    @Transactional
    void deleteByAssessment(Assessment assessment);
}