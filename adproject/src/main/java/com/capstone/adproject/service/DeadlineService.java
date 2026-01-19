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
     * ⭐ NEW: Check if a deadline title is a duplicate (ignoring whitespace)
     * @param title The title to check
     * @param deadlineIdToExclude The ID of the deadline being edited (null for new deadlines)
     * @return true if a duplicate exists, false otherwise
     */
    public boolean isTitleDuplicateIgnoringWhitespace(String title, Long deadlineIdToExclude) {
        if (title == null || title.trim().isEmpty()) {
            return false;
        }
        
        // Normalize the input title by removing all whitespace and converting to lowercase
        String normalizedTitle = title.replaceAll("\\s+", "").toLowerCase();
        
        // Get all deadlines and check for normalized matches
        List<Deadline> allDeadlines = deadlineRepository.findAll();
        
        for (Deadline deadline : allDeadlines) {
            // Skip the deadline being edited
            if (deadlineIdToExclude != null && deadline.getId().equals(deadlineIdToExclude)) {
                continue;
            }
            
            // Normalize the existing deadline title
            if (deadline.getTitle() != null) {
                String normalizedExisting = deadline.getTitle().replaceAll("\\s+", "").toLowerCase();
                
                if (normalizedExisting.equals(normalizedTitle)) {
                    return true; // Duplicate found
                }
            }
        }
        
        return false; // No duplicate found
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