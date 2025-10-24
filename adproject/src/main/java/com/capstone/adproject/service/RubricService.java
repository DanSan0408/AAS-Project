package com.capstone.adproject.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // <-- NEW IMPORT

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Criteria; 
import com.capstone.adproject.model.Rating;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.SubRubric;
import com.capstone.adproject.repositories.AssessmentRepository;
import com.capstone.adproject.repositories.RubricRepository;
import com.capstone.adproject.repositories.SubRubricRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
public class RubricService {

    private final AssessmentRepository assessmentRepository;
    private final RubricRepository rubricRepository;
    private final SubRubricRepository subRubricRepository;

    public RubricService(AssessmentRepository assessmentRepository, 
                         RubricRepository rubricRepository,
                         SubRubricRepository subRubricRepository) {
        this.assessmentRepository = assessmentRepository;
        this.rubricRepository = rubricRepository;
        this.subRubricRepository = subRubricRepository;
    }

    // --- Helper Method to Calculate Marks (Unchanged, relies on getters) ---
    public void calculateAssessmentMarks(Assessment assessment) {
        if (assessment == null) return;

        double totalMarks = 0.0;
        Map<Integer, Double> cloMarksMap = new HashMap<>();

        // 1. Calculate marks for Likert Rubrics
        if (assessment.getRubrics() != null) {
            for (Rubric rubric : assessment.getRubrics()) {
                if (rubric.getMarks() != null) {
                    totalMarks += rubric.getMarks().doubleValue(); 
                    
                    if (rubric.getClo() != null && rubric.getCloMarks() != null) {
                        cloMarksMap.merge(rubric.getClo(), rubric.getCloMarks(), Double::sum);
                    }
                }
            }
        }

        // 2. Calculate marks for Criteria Rubrics
        if (assessment.getCriteria() != null) {
            for (Criteria criteria : assessment.getCriteria()) {
                if (criteria.getMarks() != null) {
                    totalMarks += criteria.getMarks().doubleValue(); 

                    if (criteria.getClo() != null && criteria.getCloMarks() != null) {
                        cloMarksMap.merge(criteria.getClo(), criteria.getCloMarks(), Double::sum);
                    }
                }
            }
        }

        assessment.setTotalMarks(totalMarks);
        assessment.setCloMarks(cloMarksMap);
    }
    // ----------------------------------------

    public List<Assessment> findAllAssessments() {
        List<Assessment> assessments = assessmentRepository.findAll();
        for (Assessment assessment : assessments) {
            // Note: This method relies on LAZY loading and is fine for the home page list
            calculateAssessmentMarks(assessment);
        }
        return assessments;
    }

    // THE FIX IS HERE: Use @Transactional and Hibernate.initialize()
    @Transactional
    public Assessment findAssessmentById(Long id) {
        // Use standard findById, no complex JOIN FETCH needed
        Assessment assessment = assessmentRepository.findById(id) 
            .orElseThrow(() -> new EntityNotFoundException("Assessment not found with id: " + id));
            
        // 1. Initialize top-level collections (Rubrics and Criteria)
        Hibernate.initialize(assessment.getRubrics());
        Hibernate.initialize(assessment.getCriteria());
        
        // 2. Initialize nested collections for Criteria (CriteriaRatings)
        for (Criteria criteria : assessment.getCriteria()) {
            Hibernate.initialize(criteria.getCriteriaRatings()); // Assuming Criteria.criteriaRatings is List/Set
        }
        
        // 3. Initialize nested collections for Rubrics (SubRubrics and Ratings)
        for (Rubric rubric : assessment.getRubrics()) {
            Hibernate.initialize(rubric.getSubRubrics()); // Assuming Rubric.subRubrics is List/Set
            
            if (rubric.getSubRubrics() != null) {
                for (SubRubric subRubric : rubric.getSubRubrics()) {
                    Hibernate.initialize(subRubric.getRatings()); // Assuming SubRubric.ratings is List/Set
                }
            }
        }
            
        calculateAssessmentMarks(assessment);
        return assessment;
    }

    @Transactional
    public Assessment saveAssessment(Assessment assessment) {
        return assessmentRepository.save(assessment);
    }
    
    @Transactional
    public void deleteAssessment(Long id){
        if (!assessmentRepository.existsById(id)) {
            throw new EntityNotFoundException("Cannot delete. Assessment not found with id: " + id);
        }
        assessmentRepository.deleteById(id);
    }
    
    public Rubric findRubricById(Long id) {
        return rubricRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Rubric not found with id: " + id));
    }

    @Transactional
public Rubric saveRubric(Rubric rubric) {
    // 1. Ensure bidirectional relationship is set for SubRubrics and Ratings
    if (rubric.getSubRubrics() != null) {
        for (SubRubric subRubric : rubric.getSubRubrics()) {
            subRubric.setRubric(rubric);

            if (subRubric.getRatings() != null) {
                for (Rating rating : subRubric.getRatings()) {
                    rating.setSubRubric(subRubric);
                }
            }
        }
    }
    
    // 2. Load the parent Assessment to manage the bidirectional link
    if (rubric.getAssessment() != null && rubric.getAssessment().getId() != null) {
        Assessment assessment = assessmentRepository.findById(rubric.getAssessment().getId())
            .orElseThrow(() -> new EntityNotFoundException("Parent Assessment not found."));
            
        // Assuming you have an addRubric helper method on the Assessment entity.
        // If not, you should add one, or use assessment.getRubrics().add(rubric);
        assessment.addRubric(rubric); 
        // Note: assessmentRepository.save(assessment) is usually not strictly required 
        // here due to the transactional context and the cascading set on Assessment.rubrics,
        // but it ensures the parent object is marked dirty if necessary.
    }

    // 3. Save the Rubric (This will cascade up and save the Assessment if necessary)
    // The Rubric is returned, which the controller will use for the redirect ID.
    return rubricRepository.save(rubric);
}

    @Transactional
    public void deleteRubric(Long rubricId) {
        if (!rubricRepository.existsById(rubricId)) {
            throw new EntityNotFoundException("Cannot delete. Rubric not found with id: " + rubricId);
        }
        rubricRepository.deleteById(rubricId);
    }
}