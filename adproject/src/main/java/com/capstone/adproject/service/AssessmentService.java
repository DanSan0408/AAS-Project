package com.capstone.adproject.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.repositories.AssessmentRepository;

@Service
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;

    public AssessmentService(AssessmentRepository assessmentRepository) {
        this.assessmentRepository = assessmentRepository;
    }

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

    /**
     * ✅ FIXED: Find all assessments with rubrics and calculate marks safely
     * Handles null marks values to prevent NullPointerException
     */
    public List<Assessment> findAllAssessmentsWithRubrics() {
        List<Assessment> assessments = assessmentRepository.findAll();
        
        for (Assessment assessment : assessments) {
            calculateAssessmentMarks(assessment);
        }
        
        return assessments;
    }

    /**
     * ✅ NEW: Safe calculation of assessment marks with null checks
     * Calculates total marks and CLO marks for an assessment
     */
    private void calculateAssessmentMarks(Assessment assessment) {
        if (assessment == null || assessment.getRubrics() == null) {
            return;
        }

        double totalMarks = 0.0;
        Map<Integer, Double> cloMarksMap = new HashMap<>();

        for (Rubric rubric : assessment.getRubrics()) {
            // ✅ FIX: Add null check before calling doubleValue()
            if (rubric.getMarks() != null) {
                totalMarks += rubric.getMarks().doubleValue();
                
                // Calculate CLO marks if available
                if (rubric.getClo() != null && rubric.getCloMarks() != null) {
                    cloMarksMap.merge(rubric.getClo(), rubric.getCloMarks(), Double::sum);
                }
            } else {
                // ✅ Optional: Log warning for debugging
                System.out.println("WARNING: Rubric '" + rubric.getName() + 
                                   "' (ID: " + rubric.getId() + 
                                   ") in Assessment '" + assessment.getTitle() + 
                                   "' has null marks");
            }
        }

        assessment.setTotalMarks(totalMarks);
        assessment.setCloMarks(cloMarksMap);
    }

    public Optional<Assessment> getAssessmentById(Long id) {
        return assessmentRepository.findById(id); 
    }

    /**
     * Find assessment by title
     * Used by Industrial Supervisor to get the specific assessment they evaluate
     * Returns the first match if multiple assessments have the same title
     */
    public Optional<Assessment> findByTitle(String title) {
        List<Assessment> assessments = assessmentRepository.findByTitle(title);
        return assessments.isEmpty() ? Optional.empty() : Optional.of(assessments.get(0));
    }
}