package com.capstone.adproject.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.model.Assessment; // Import Transactional
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.repositories.AssessmentRepository;

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
    if (title == null || title.trim().isEmpty()) {
        return false;
    }
    
    // Normalize the input title by removing all whitespace and converting to lowercase
    String normalizedTitle = title.replaceAll("\\s+", "").toLowerCase();
    
    List<Assessment> allAssessments = assessmentRepository.findAll();
    
    for (Assessment assessment : allAssessments) {
        // Skip the assessment being edited
        if (assessmentIdToExclude != null && assessment.getId().equals(assessmentIdToExclude)) {
            continue;
        }
        
        if (assessment.getTitle() != null) {
            String normalizedExisting = assessment.getTitle().replaceAll("\\s+", "").toLowerCase();
            if (normalizedExisting.equals(normalizedTitle)) {
                return true;
            }
        }
    }
    
    return false;
}

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

    public Optional<Assessment> getAssessmentById(Long id) {
    // Assuming you have an AssessmentRepository
    return assessmentRepository.findById(id); 
}
}