package com.capstone.adproject.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.capstone.adproject.model.AssessmentSubmission;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.SubmissionAnnotation;

@Repository
public interface SubmissionAnnotationRepository extends JpaRepository<SubmissionAnnotation, Long> {

    List<SubmissionAnnotation> findBySubmission(AssessmentSubmission submission);

    @Query("SELECT a FROM SubmissionAnnotation a WHERE a.submission.id = :submissionId")
    List<SubmissionAnnotation> findBySubmissionId(@Param("submissionId") Long submissionId);

    List<SubmissionAnnotation> findBySubmissionAndLecturer(AssessmentSubmission submission, Lecturer lecturer);

    Optional<SubmissionAnnotation> findBySubmissionAndLecturerAndPageNumber(AssessmentSubmission submission, Lecturer lecturer, Integer pageNumber);

    List<SubmissionAnnotation> findBySubmissionOrderByPageNumberAsc(AssessmentSubmission submission);
}

