package com.capstone.adproject.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            calculateAssessmentMarks(assessment);
        }
        return assessments;
    }

    @Transactional
    public Assessment findAssessmentById(Long id) {
        Assessment assessment = assessmentRepository.findById(id) 
            .orElseThrow(() -> new EntityNotFoundException("Assessment not found with id: " + id));
            
        Hibernate.initialize(assessment.getRubrics());
        Hibernate.initialize(assessment.getCriteria());
        
        for (Criteria criteria : assessment.getCriteria()) {
            Hibernate.initialize(criteria.getCriteriaRatings());
        }
        
        for (Rubric rubric : assessment.getRubrics()) {
            Hibernate.initialize(rubric.getSubRubrics());
            
            if (rubric.getSubRubrics() != null) {
                for (SubRubric subRubric : rubric.getSubRubrics()) {
                    Hibernate.initialize(subRubric.getRatings());
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
    public Rubric saveRubric(Rubric formRubric) { 
        
        // Logic for UPDATING an existing Rubric (formRubric.getId() != null)
        if (formRubric.getId() != null) {
            
            // 1. Fetch the existing, managed Rubric entity
            Rubric existingRubric = rubricRepository.findById(formRubric.getId())
                .orElseThrow(() -> new EntityNotFoundException("Rubric not found with id: " + formRubric.getId()));
                
            // 2. Update scalar fields
            existingRubric.setName(formRubric.getName());
            existingRubric.setMarks(formRubric.getMarks());
            existingRubric.setEvaluationType(formRubric.getEvaluationType());
            existingRubric.setAssessmentTypes(formRubric.getAssessmentTypes());
            existingRubric.setClo(formRubric.getClo());
            existingRubric.setCloMarks(formRubric.getCloMarks());

            
            // 3. --- SubRubrics Collection Management (Outer Collection) ---
            List<SubRubric> managedSubRubrics = existingRubric.getSubRubrics(); 

            Set<Long> formSubRubricIds = formRubric.getSubRubrics().stream()
                                                 .map(SubRubric::getId)
                                                 .filter(java.util.Objects::nonNull)
                                                 .collect(Collectors.toSet());

            managedSubRubrics.removeIf(sr -> sr.getId() != null && !formSubRubricIds.contains(sr.getId()));
            
            Map<Long, SubRubric> existingSubRubricMap = managedSubRubrics.stream()
                .filter(sr -> sr.getId() != null)
                .collect(Collectors.toMap(SubRubric::getId, sr -> sr));


            // 4. Iterate and update/add SubRubrics
            List<SubRubric> updatedSubRubricsList = new ArrayList<>();
            if (formRubric.getSubRubrics() != null) {
                for (SubRubric formSubRubric : formRubric.getSubRubrics()) {
                    
                    SubRubric targetSubRubric;

                    if (formSubRubric.getId() != null && existingSubRubricMap.containsKey(formSubRubric.getId())) {
                        // **UPDATE EXISTING SubRubric**
                        targetSubRubric = existingSubRubricMap.get(formSubRubric.getId());
                        targetSubRubric.setDescription(formSubRubric.getDescription());
                        targetSubRubric.setName(formSubRubric.getName()); 
                        
                        // --- 5. Ratings Collection Management (Nested Collection - ULTIMATE CORE FIX) ---
                        List<Rating> managedRatings = targetSubRubric.getRatings();
                        List<Rating> formRatings = formSubRubric.getRatings() != null ? formSubRubric.getRatings() : new ArrayList<>();
                        
                        // Get IDs from the incoming form data
                        Set<Long> formRatingIdsNested = formRatings.stream()
                                                         .map(Rating::getId)
                                                         .filter(java.util.Objects::nonNull)
                                                         .collect(Collectors.toSet());
                        
                        // CRITICAL STEP B: Remove old orphans from the managed collection
                        managedRatings.removeIf(r -> r.getId() != null && !formRatingIdsNested.contains(r.getId()));
                        
                        // Prepare a map of existing Ratings by ID *after* orphan removal
                        Map<Long, Rating> existingRatingMap = managedRatings.stream()
                                                         .filter(r -> r.getId() != null)
                                                         .collect(Collectors.toMap(Rating::getId, r -> r));

                        
                        // List to build the final, ordered collection for assignment
                        List<Rating> finalOrderedRatings = new ArrayList<>();

                        // Iterate over FORM list to update, RE-ASSOCIATE, and collect
                        for (Rating formRating : formRatings) {
                            
                            Rating entityToSave;
                            
                            if (formRating.getId() != null && existingRatingMap.containsKey(formRating.getId())) {
                                // Update existing managed Rating
                                entityToSave = existingRatingMap.get(formRating.getId());
                                entityToSave.setDescription(formRating.getDescription());
                                entityToSave.setLevel(formRating.getLevel());
                                
                                // ⭐ ULTIMATE FIX: Explicitly call the setter on the managed entity.
                                entityToSave.setSubRubric(targetSubRubric); 
                                
                            } else {
                                // This is a NEW Rating
                                entityToSave = formRating;
                                
                                // Ensure bidirectional link is set for the new entity
                                entityToSave.setSubRubric(targetSubRubric); 
                            }
                            
                            finalOrderedRatings.add(entityToSave);
                        }

                        // Replace the managed list content with the final, ordered list.
                        managedRatings.clear(); 
                        managedRatings.addAll(finalOrderedRatings);
                        // --- END ULTIMATE CORE FIX ---
                        
                    } else {
                        // **ADD NEW SubRubric**
                        targetSubRubric = formSubRubric;
                        // Ensure all new children (Ratings) point to the new SubRubric
                        if (targetSubRubric.getRatings() != null) {
                            targetSubRubric.getRatings().forEach(r -> r.setSubRubric(targetSubRubric));
                        }
                    }
                    
                    // Ensure parent link for SubRubric
                    targetSubRubric.setRubric(existingRubric); 
                    updatedSubRubricsList.add(targetSubRubric);
                }
            }
            
            // Final step for the outer collection: replace the managed list with the list from the form
            managedSubRubrics.clear(); 
            managedSubRubrics.addAll(updatedSubRubricsList);
            
            return rubricRepository.save(existingRubric);
            
        } else {
            // ... (Logic for SAVING a BRAND NEW Rubric - remains the same) ...
            
            if (formRubric.getSubRubrics() != null) {
                for (SubRubric subRubric : formRubric.getSubRubrics()) {
                    subRubric.setRubric(formRubric);
                    
                    if (subRubric.getRatings() != null) {
                        for (Rating rating : subRubric.getRatings()) {
                            rating.setSubRubric(subRubric);
                        }
                    }
                }
            }
            
            // Load parent Assessment and save
            if (formRubric.getAssessment() != null && formRubric.getAssessment().getId() != null) {
                Assessment assessment = assessmentRepository.findById(formRubric.getAssessment().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Parent Assessment not found."));
                formRubric.setAssessment(assessment); 
            }

            return rubricRepository.save(formRubric);
        }
    }

    @Transactional
    public void deleteRubric(Long rubricId) {
        if (!rubricRepository.existsById(rubricId)) {
            throw new EntityNotFoundException("Cannot delete. Rubric not found with id: " + rubricId);
        }
        rubricRepository.deleteById(rubricId);
    }

    public boolean isRubricNameDuplicate(String name, Long assessmentId, Long rubricIdToExclude) {
    if (name == null || name.trim().isEmpty()) {
        return false;
    }
    
    // Normalize the input name by removing all whitespace and converting to lowercase
    String normalizedName = name.replaceAll("\\s+", "").toLowerCase();
    
    List<Rubric> existingRubrics = rubricRepository.findByNameAndAssessmentId(name, assessmentId);
    
    // Also check all rubrics in the assessment with normalized names
    Assessment assessment = assessmentRepository.findById(assessmentId).orElse(null);
    if (assessment != null && assessment.getRubrics() != null) {
        for (Rubric rubric : assessment.getRubrics()) {
            // Skip the rubric being edited
            if (rubricIdToExclude != null && rubric.getId().equals(rubricIdToExclude)) {
                continue;
            }
            
            if (rubric.getName() != null) {
                String normalizedExisting = rubric.getName().replaceAll("\\s+", "").toLowerCase();
                if (normalizedExisting.equals(normalizedName)) {
                    return true;
                }
            }
        }
    }
    
    return false;
}

    
}