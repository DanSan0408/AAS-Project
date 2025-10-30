package com.capstone.adproject.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.model.Criteria;
import com.capstone.adproject.repositories.AssessmentRepository;
import com.capstone.adproject.repositories.CriteriaRepository;

import jakarta.persistence.EntityNotFoundException;
import java.util.Optional;

@Service
public class CriteriaService {

    private final CriteriaRepository criteriaRepository;
    private final AssessmentRepository assessmentRepository; // Needed for assessment linking

    public CriteriaService(CriteriaRepository criteriaRepository, AssessmentRepository assessmentRepository) {
        this.criteriaRepository = criteriaRepository;
        this.assessmentRepository = assessmentRepository;
    }

    public boolean isCriteriaNameDuplicate(String name, Long assessmentId, Long currentCriteriaId) {
        if (currentCriteriaId == null) {
            // New criteria check: look for any criteria with the name and assessmentId
            return criteriaRepository.existsByNameAndAssessmentId(name, assessmentId);
        } else {
            // Edit criteria check: look for criteria with the same name and assessmentId, but a DIFFERENT id
            Optional<Criteria> existingCriteria = criteriaRepository.findByNameAndAssessmentId(name, assessmentId);
            // It's a duplicate if: 
            // 1. A criteria exists, AND
            // 2. The existing criteria's ID is not the one we are currently editing
            return existingCriteria.isPresent() && !existingCriteria.get().getId().equals(currentCriteriaId);
        }
    }

    public Criteria findCriteriaById(Long id) {
        return criteriaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Criteria not found with id: " + id));
    }

    @Transactional
    public Criteria saveCriteria(Criteria criteria) {
        // Core logic: Set the cloMarks to be the same as the criteria marks (total marks)
        if (criteria.getMarks() != null) {
            criteria.setCloMarks(criteria.getMarks().doubleValue());
        } else {
            criteria.setCloMarks(null);
        }

        // Set the bidirectional relationships before saving.
        criteria.getCriteriaRatings().forEach(rating -> rating.setCriteria(criteria));

        return criteriaRepository.save(criteria);
    }

    @Transactional
public void deleteCriteria(Long criteriaId) {
    Criteria criteria = criteriaRepository.findById(criteriaId)
        .orElseThrow(() -> new EntityNotFoundException("Criteria not found with id: " + criteriaId));
    criteriaRepository.delete(criteria);
}
}