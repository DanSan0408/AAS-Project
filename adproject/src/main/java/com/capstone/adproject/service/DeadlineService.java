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
    
    public boolean isTitleDuplicateIgnoringWhitespace(String title, Long deadlineIdToExclude) {
        if (title == null || title.trim().isEmpty()) {
            return false;
        }
        
        String normalizedTitle = title.replaceAll("\\s+", "").toLowerCase();
        
        List<Deadline> allDeadlines = deadlineRepository.findAll();
        
        for (Deadline deadline : allDeadlines) {
            
            if (deadlineIdToExclude != null && deadline.getId().equals(deadlineIdToExclude)) {
                continue;
            }
            
            if (deadline.getTitle() != null) {
                String normalizedExisting = deadline.getTitle().replaceAll("\\s+", "").toLowerCase();
                
                if (normalizedExisting.equals(normalizedTitle)) {
                    return true; 
                }
            }
        }
        
        return false; 
    }
    
    public List<Deadline> getDeadlinesByAssessmentId(Long assessmentId) {
        return deadlineRepository.findByAssessmentId(assessmentId);
    }
    
    public List<Deadline> getDeadlinesByAssessorType(String assessorType) {
        return deadlineRepository.findByAssessorType(assessorType);
    }
    
    public List<Deadline> getDeadlinesByAssessmentIdAndAssessorType(Long assessmentId, String assessorType) {
        return deadlineRepository.findByAssessmentIdAndAssessorType(assessmentId, assessorType);
    }
}