package com.capstone.adproject.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.capstone.adproject.model.Deadline;
import com.capstone.adproject.repositories.DeadlineRepository;

@Service
public class DeadlineService {

    private final DeadlineRepository deadlineRepository;

    @Autowired
    public DeadlineService(DeadlineRepository deadlineRepository) {
        this.deadlineRepository = deadlineRepository;
    }

    public List<Deadline> getAllDeadlines() {
        return deadlineRepository.findAll();
    }

    public Optional<Deadline> getDeadlineById(Long id) {
        return deadlineRepository.findById(id);
    }

    public void saveAllDeadlines(List<Deadline> deadlines) {
        deadlineRepository.saveAll(deadlines);
    }

    public void deleteDeadline(Long id) {
        deadlineRepository.deleteById(id);
    }

    public void save(Deadline deadline) {
        deadlineRepository.save(deadline);
    }

    public Optional<Deadline> findByTitle(String title) {
        return deadlineRepository.findByTitle(title);
    }
    
    /**
     * ⭐ NEW: Get deadlines by assessment ID
     */
    public List<Deadline> getDeadlinesByAssessmentId(Long assessmentId) {
        return deadlineRepository.findByAssessmentId(assessmentId);
    }
    
    /**
     * ⭐ NEW: Get deadlines by assessor type (STUDENT, LECTURER, SUPERVISOR)
     */
    public List<Deadline> getDeadlinesByAssessorType(String assessorType) {
        return deadlineRepository.findByAssessorType(assessorType);
    }
    
    /**
     * ⭐ NEW: Get deadlines by assessment ID and assessor type
     * This is the key method for checking if an assessment is open for a specific user role
     */
    public List<Deadline> getDeadlinesByAssessmentIdAndAssessorType(Long assessmentId, String assessorType) {
        return deadlineRepository.findByAssessmentIdAndAssessorType(assessmentId, assessorType);
    }
}