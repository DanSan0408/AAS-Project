package com.capstone.adproject.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.AssessmentSubmission;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Student;

@Repository
public interface AssessmentSubmissionRepository extends JpaRepository<AssessmentSubmission, Long> {

    Optional<AssessmentSubmission> findByAssessmentAndGroup(Assessment assessment, Group group);

    Optional<AssessmentSubmission> findByAssessmentAndStudent(Assessment assessment, Student student);

    List<AssessmentSubmission> findByAssessment(Assessment assessment);

    List<AssessmentSubmission> findByAssessmentId(Long assessmentId);

    List<AssessmentSubmission> findByCourseId(Long courseId);

    List<AssessmentSubmission> findByGroupId(Long groupId);

    List<AssessmentSubmission> findByStudentId(Long studentId);
}

