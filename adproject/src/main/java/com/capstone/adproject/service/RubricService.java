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
import com.capstone.adproject.model.Mark;
import com.capstone.adproject.model.Rating;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.SubRubric;
import com.capstone.adproject.repositories.AssessmentRepository;
import com.capstone.adproject.repositories.MarkRepository;
import com.capstone.adproject.repositories.RubricRepository;
import com.capstone.adproject.repositories.SubRubricRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
public class RubricService {

    private final AssessmentRepository assessmentRepository;
    private final RubricRepository rubricRepository;
    private final SubRubricRepository subRubricRepository;
    private final MarkRepository markRepository;  // ✅ NEW

    public RubricService(AssessmentRepository assessmentRepository, 
                         RubricRepository rubricRepository,
                         SubRubricRepository subRubricRepository,
                         MarkRepository markRepository) {  // ✅ NEW
        this.assessmentRepository = assessmentRepository;
        this.rubricRepository = rubricRepository;
        this.subRubricRepository = subRubricRepository;
        this.markRepository = markRepository;  // ✅ NEW
    }

    // Helper Method to Calculate Marks
    public void calculateAssessmentMarks(Assessment assessment) {
        if (assessment == null) return;

        double totalMarks = 0.0;
        Map<Integer, Double> cloMarksMap = new HashMap<>();

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

        assessment.setTotalMarks(totalMarks);
        assessment.setCloMarks(cloMarksMap);
    }

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
        
        for (Rubric rubric : assessment.getRubrics()) {
            Hibernate.initialize(rubric.getSubRubrics());
            Hibernate.initialize(rubric.getRatings());
            
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
    
    /**
     * ✅ UPDATED: Delete assessment with cascade deletion of marks
     */
    @Transactional
    public void deleteAssessment(Long id){
        if (!assessmentRepository.existsById(id)) {
            throw new EntityNotFoundException("Cannot delete. Assessment not found with id: " + id);
        }
        
        Assessment assessment = assessmentRepository.findById(id).orElse(null);
        if (assessment != null && assessment.getRubrics() != null) {
            // Delete marks for all rubrics in this assessment
            for (Rubric rubric : assessment.getRubrics()) {
                deleteMarksForRubric(rubric);
            }
        }
        
        assessmentRepository.deleteById(id);
    }
    
    @Transactional
    public Rubric findRubricById(Long id) {
        Rubric rubric = rubricRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Rubric not found with id: " + id));
        
        // Initialize lazy collections
        Hibernate.initialize(rubric.getSubRubrics());
        Hibernate.initialize(rubric.getRatings());
        
        if (rubric.getSubRubrics() != null) {
            for (SubRubric sr : rubric.getSubRubrics()) {
                Hibernate.initialize(sr.getRatings());
            }
        }
        
        return rubric;
    }


    @Transactional
    public Rubric saveRubric(Rubric formRubric) { 
        
        // Logic for UPDATING an existing Rubric
        if (formRubric.getId() != null) {
            
            Rubric existingRubric = rubricRepository.findById(formRubric.getId())
                .orElseThrow(() -> new EntityNotFoundException("Rubric not found with id: " + formRubric.getId()));
                
            // Update scalar fields
            existingRubric.setName(formRubric.getName());
            existingRubric.setDescription(formRubric.getDescription());
            existingRubric.setMarks(formRubric.getMarks());
            existingRubric.setAssessmentTypes(formRubric.getAssessmentTypes());
            existingRubric.setClo(formRubric.getClo());
            existingRubric.setCloMarks(formRubric.getCloMarks());

            // --- Handle SubRubrics Collection ---
            updateSubRubrics(existingRubric, formRubric.getSubRubrics());
            
            // --- Handle Direct Ratings Collection ---
            updateDirectRatings(existingRubric, formRubric.getRatings());
            
            return rubricRepository.save(existingRubric);
            
        } else {
            // Logic for SAVING a BRAND NEW Rubric
            
            if (formRubric.getSubRubrics() != null) {
                for (SubRubric subRubric : formRubric.getSubRubrics()) {
                    subRubric.setRubric(formRubric);
                    
                    if (subRubric.getRatings() != null) {
                        for (Rating rating : subRubric.getRatings()) {
                            rating.setSubRubric(subRubric);
                            rating.setRubric(null);
                        }
                    }
                }
            }
            
            if (formRubric.getRatings() != null) {
                for (Rating rating : formRubric.getRatings()) {
                    rating.setRubric(formRubric);
                    rating.setSubRubric(null);
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

    private void updateSubRubrics(Rubric existingRubric, List<SubRubric> formSubRubrics) {
        List<SubRubric> managedSubRubrics = existingRubric.getSubRubrics();
        
        if (formSubRubrics == null) {
            formSubRubrics = new ArrayList<>();
        }

        Set<Long> formSubRubricIds = formSubRubrics.stream()
                                             .map(SubRubric::getId)
                                             .filter(java.util.Objects::nonNull)
                                             .collect(Collectors.toSet());

        // Remove orphans
        managedSubRubrics.removeIf(sr -> sr.getId() != null && !formSubRubricIds.contains(sr.getId()));
        
        Map<Long, SubRubric> existingSubRubricMap = managedSubRubrics.stream()
            .filter(sr -> sr.getId() != null)
            .collect(Collectors.toMap(SubRubric::getId, sr -> sr));

        List<SubRubric> updatedSubRubricsList = new ArrayList<>();
        
        for (SubRubric formSubRubric : formSubRubrics) {
            SubRubric targetSubRubric;

            if (formSubRubric.getId() != null && existingSubRubricMap.containsKey(formSubRubric.getId())) {
                // Update existing SubRubric
                targetSubRubric = existingSubRubricMap.get(formSubRubric.getId());
                targetSubRubric.setDescription(formSubRubric.getDescription());
                targetSubRubric.setName(formSubRubric.getName()); 
                targetSubRubric.setMarks(formSubRubric.getMarks());
                
                // Update ratings within this sub-rubric
                updateSubRubricRatings(targetSubRubric, formSubRubric.getRatings());
                
            } else {
                // Add new SubRubric
                targetSubRubric = formSubRubric;
                if (targetSubRubric.getRatings() != null) {
                    targetSubRubric.getRatings().forEach(r -> {
                        r.setSubRubric(targetSubRubric);
                        r.setRubric(null);
                    });
                }
            }
            
            targetSubRubric.setRubric(existingRubric); 
            updatedSubRubricsList.add(targetSubRubric);
        }
        
        managedSubRubrics.clear(); 
        managedSubRubrics.addAll(updatedSubRubricsList);
    }

    private void updateSubRubricRatings(SubRubric targetSubRubric, List<Rating> formRatings) {
        List<Rating> managedRatings = targetSubRubric.getRatings();
        
        if (formRatings == null) {
            formRatings = new ArrayList<>();
        }
        
        Set<Long> formRatingIds = formRatings.stream()
                                     .map(Rating::getId)
                                     .filter(java.util.Objects::nonNull)
                                     .collect(Collectors.toSet());
        
        // Remove orphans
        managedRatings.removeIf(r -> r.getId() != null && !formRatingIds.contains(r.getId()));
        
        Map<Long, Rating> existingRatingMap = managedRatings.stream()
                                     .filter(r -> r.getId() != null)
                                     .collect(Collectors.toMap(Rating::getId, r -> r));

        List<Rating> finalOrderedRatings = new ArrayList<>();

        for (Rating formRating : formRatings) {
            Rating entityToSave;
            
            if (formRating.getId() != null && existingRatingMap.containsKey(formRating.getId())) {
                entityToSave = existingRatingMap.get(formRating.getId());
                entityToSave.setName(formRating.getName());
                entityToSave.setDescription(formRating.getDescription());
                entityToSave.setMarks(formRating.getMarks());
                entityToSave.setSubRubric(targetSubRubric); 
                entityToSave.setRubric(null);
            } else {
                entityToSave = formRating;
                entityToSave.setSubRubric(targetSubRubric); 
                entityToSave.setRubric(null);
            }
            
            finalOrderedRatings.add(entityToSave);
        }

        managedRatings.clear(); 
        managedRatings.addAll(finalOrderedRatings);
    }

    private void updateDirectRatings(Rubric existingRubric, List<Rating> formRatings) {
        List<Rating> managedRatings = existingRubric.getRatings();
        
        if (formRatings == null) {
            formRatings = new ArrayList<>();
        }
        
        Set<Long> formRatingIds = formRatings.stream()
                                     .map(Rating::getId)
                                     .filter(java.util.Objects::nonNull)
                                     .collect(Collectors.toSet());
        
        // Remove orphans
        managedRatings.removeIf(r -> r.getId() != null && !formRatingIds.contains(r.getId()));
        
        Map<Long, Rating> existingRatingMap = managedRatings.stream()
                                     .filter(r -> r.getId() != null)
                                     .collect(Collectors.toMap(Rating::getId, r -> r));

        List<Rating> finalOrderedRatings = new ArrayList<>();

        for (Rating formRating : formRatings) {
            Rating entityToSave;
            
            if (formRating.getId() != null && existingRatingMap.containsKey(formRating.getId())) {
                entityToSave = existingRatingMap.get(formRating.getId());
                entityToSave.setName(formRating.getName());
                entityToSave.setDescription(formRating.getDescription());
                entityToSave.setMarks(formRating.getMarks());
                entityToSave.setRubric(existingRubric); 
                entityToSave.setSubRubric(null);
            } else {
                entityToSave = formRating;
                entityToSave.setRubric(existingRubric); 
                entityToSave.setSubRubric(null);
            }
            
            finalOrderedRatings.add(entityToSave);
        }

        managedRatings.clear(); 
        managedRatings.addAll(finalOrderedRatings);
    }

    /**
     * ✅ UPDATED: Delete rubric with cascade deletion of marks
     * Deletes all marks associated with the rubric and its sub-rubrics before deleting the rubric
     */
    @Transactional
    public void deleteRubric(Long rubricId) {
        if (!rubricRepository.existsById(rubricId)) {
            throw new EntityNotFoundException("Cannot delete. Rubric not found with id: " + rubricId);
        }
        
        Rubric rubric = rubricRepository.findById(rubricId).orElse(null);
        if (rubric != null) {
            // ✅ NEW: Delete all marks first
            deleteMarksForRubric(rubric);
        }
        
        rubricRepository.deleteById(rubricId);
    }

    /**
     * ✅ NEW: Helper method to delete all marks associated with a rubric
     * Handles marks for:
     * - The rubric itself
     * - All sub-rubrics under the rubric
     */
    private void deleteMarksForRubric(Rubric rubric) {
        // Delete marks directly associated with this rubric
        List<Mark> rubricMarks = markRepository.findByRubric(rubric);
        if (!rubricMarks.isEmpty()) {
            markRepository.deleteAll(rubricMarks);
        }
        
        // Delete marks associated with sub-rubrics
        if (rubric.getSubRubrics() != null) {
            for (SubRubric subRubric : rubric.getSubRubrics()) {
                List<Mark> subRubricMarks = markRepository.findBySubRubric(subRubric);
                if (!subRubricMarks.isEmpty()) {
                    markRepository.deleteAll(subRubricMarks);
                }
            }
        }
    }

    public boolean isRubricNameDuplicate(String name, Long assessmentId, Long rubricIdToExclude) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        
        // Normalize the input name by removing all whitespace and converting to lowercase
        String normalizedName = name.replaceAll("\\s+", "").toLowerCase();
        
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