package com.capstone.adproject.service;

import java.util.Collections;
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

    @Transactional(readOnly = true)
    public boolean isAssessmentTitleDuplicate(String title, Long assessmentIdToExclude) {
        if (title == null || title.trim().isEmpty()) {
            return false;
        }
        
        String normalizedTitle = title.replaceAll("\\s+", "").toLowerCase();
        
        List<Assessment> allAssessments = assessmentRepository.findAll();
        
        for (Assessment assessment : allAssessments) {
            
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
            calculateAssessmentMarks(assessment);
        }
        
        return assessments;
    }

    public List<Assessment> findAllAssessmentsWithRubricsByCourseId(Long courseId) {
        if (courseId == null) {
            return Collections.emptyList();
        }

        List<Assessment> assessments = assessmentRepository.findAllWithRubricsByCourseId(courseId);
        for (Assessment assessment : assessments) {
            calculateAssessmentMarks(assessment);
        }
        return assessments;
    }

    private void calculateAssessmentMarks(Assessment assessment) {
        if (assessment == null || assessment.getRubrics() == null) {
            return;
        }

        double totalMarks = 0.0;
        Map<Integer, Double> cloMarksMap = new HashMap<>();

        for (Rubric rubric : assessment.getRubrics()) {
            if (rubric.getMarks() != null) {
                totalMarks += rubric.getMarks().doubleValue();
             
                if (rubric.getClo() != null && rubric.getCloMarks() != null) {
                    cloMarksMap.merge(rubric.getClo(), rubric.getCloMarks(), Double::sum);
                }
            } else {
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

    public Optional<Assessment> findByTitle(String title) {
        List<Assessment> assessments = assessmentRepository.findByTitle(title);
        return assessments.isEmpty() ? Optional.empty() : Optional.of(assessments.get(0));
    }
}