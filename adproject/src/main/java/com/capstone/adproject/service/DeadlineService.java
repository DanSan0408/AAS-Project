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

    // ⭐ NEW METHOD: Check if a deadline with the given title exists
    public Optional<Deadline> findByTitle(String title) {
        // ASSUMPTION: You have defined a method in your DeadlineRepository like: 
        // Optional<Deadline> findByTitle(String title);
        return deadlineRepository.findByTitle(title);
    }
}