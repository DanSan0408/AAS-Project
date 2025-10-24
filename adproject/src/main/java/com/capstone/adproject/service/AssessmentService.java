package com.capstone.adproject.service;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.repositories.AssessmentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;

    public AssessmentService(AssessmentRepository assessmentRepository) {
        this.assessmentRepository = assessmentRepository;
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
}