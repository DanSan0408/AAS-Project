package com.capstone.adproject.service;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.repositories.AssessmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Import Transactional

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;

    public AssessmentService(AssessmentRepository assessmentRepository) {
        this.assessmentRepository = assessmentRepository;
    }

    // --- NEW DUPLICATE CHECK METHOD ---
    /**
     * Checks if an assessment title is a duplicate.
     * @param title The title to check.
     * @param assessmentIdToExclude The ID of the assessment being edited (null for new creation).
     * @return true if a duplicate is found (excluding the one being edited), false otherwise.
     */
    @Transactional(readOnly = true)
    public boolean isAssessmentTitleDuplicate(String title, Long assessmentIdToExclude) {
        // 1. Find all assessments with the given title
        List<Assessment> existingAssessments = assessmentRepository.findByTitle(title);
        
        // 2. Filter out the assessment being edited (if it's an update)
        if (assessmentIdToExclude != null) {
            existingAssessments.removeIf(assessment -> 
                assessment.getId() != null && assessment.getId().equals(assessmentIdToExclude)
            );
        }
        
        // 3. If any remain, it's a duplicate
        return !existingAssessments.isEmpty();
    }
    // ------------------------------------

    public List<Assessment> findAllAssessmentsWithRubrics() {
        List<Assessment> assessments = assessmentRepository.findAll();
        for (Assessment assessment : assessments) {
            // Recalculate total marks and CLO marks
            double totalMarks = 0;
            for (Rubric rubric : assessment.getRubrics()) {
                totalMarks += rubric.getMarks().doubleValue();
            }
            assessment.setTotalMarks(totalMarks);
            // Assuming CLO marks are calculated from rubrics. This logic needs to be
            // implemented in the model or here. For now, it's a placeholder.
        }
        return assessments;
    }
}