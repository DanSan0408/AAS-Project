package com.capstone.adproject.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.model.Criteria;
import com.capstone.adproject.repositories.AssessmentRepository;
import com.capstone.adproject.repositories.CriteriaRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
public class CriteriaService {

    private final CriteriaRepository criteriaRepository;
    private final AssessmentRepository assessmentRepository; // Needed for assessment linking

    public CriteriaService(CriteriaRepository criteriaRepository, AssessmentRepository assessmentRepository) {
        this.criteriaRepository = criteriaRepository;
        this.assessmentRepository = assessmentRepository;
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