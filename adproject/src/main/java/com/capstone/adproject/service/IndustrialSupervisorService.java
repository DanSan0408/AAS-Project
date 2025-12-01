package com.capstone.adproject.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.IndustrialSupervisor;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.IndustrialSupervisorRepository;

@Service
@Transactional
public class IndustrialSupervisorService {
    
    private final IndustrialSupervisorRepository industrialSupervisorRepository;
    private final GroupRepository groupRepository;
    private final PasswordEncoder passwordEncoder;
    
    public IndustrialSupervisorService(
            IndustrialSupervisorRepository industrialSupervisorRepository,
            GroupRepository groupRepository,
            PasswordEncoder passwordEncoder) {
        this.industrialSupervisorRepository = industrialSupervisorRepository;
        this.groupRepository = groupRepository;
        this.passwordEncoder = passwordEncoder;
    }
    
    /**
     * Find industrial supervisor by username
     */
    public Optional<IndustrialSupervisor> findByUsername(String username) {
        return industrialSupervisorRepository.findByUsername(username);
    }
    
    /**
     * Find industrial supervisor by email
     */
    public Optional<IndustrialSupervisor> findByEmail(String email) {
        return industrialSupervisorRepository.findByEmail(email);
    }
    
    /**
     * Get all groups assigned to a specific industrial supervisor
     */
    public List<Group> getAssignedGroups(Long supervisorId) {
        return groupRepository.findByIndustrialSupervisorId(supervisorId);
    }
    
    /**
     * Get all groups assigned to a specific industrial supervisor by username
     */
    public List<Group> getAssignedGroupsByUsername(String username) {
        Optional<IndustrialSupervisor> supervisor = findByUsername(username);
        if (supervisor.isPresent()) {
            return getAssignedGroups(supervisor.get().getId());
        }
        return List.of();
    }
    
    /**
     * Save or update industrial supervisor
     */
    public IndustrialSupervisor save(IndustrialSupervisor supervisor) {
        return industrialSupervisorRepository.save(supervisor);
    }
    
    /**
     * Create new industrial supervisor with encoded password
     */
    public IndustrialSupervisor createSupervisor(String username, String password, String email) {
        IndustrialSupervisor supervisor = new IndustrialSupervisor();
        supervisor.setUsername(username);
        supervisor.setPassword(passwordEncoder.encode(password));
        supervisor.setEmail(email);
        return save(supervisor);
    }
    
    /**
     * Check if username exists
     */
    public boolean existsByUsername(String username) {
        return industrialSupervisorRepository.existsByUsername(username);
    }
    
    /**
     * Check if email exists
     */
    public boolean existsByEmail(String email) {
        return industrialSupervisorRepository.existsByEmail(email);
    }
    
    /**
     * Check if group evaluation exists
     */
    public boolean hasGroupEvaluation(Long supervisorId, Long groupId, Long assessmentId) {
        // TODO: Implement check for existing group evaluation
        // Query marks table for supervisor + any student in group + assessment + group rubrics
        return false;
    }
    
    /**
     * Get existing group evaluation ratings
     */
    public Map<Long, Long> getGroupEvaluationRatings(Long supervisorId, Long groupId, Long assessmentId) {
        // TODO: Implement fetching existing ratings for group evaluation
        // Return map of rubricId -> ratingId
        return new HashMap<>();
    }
    
    /**
     * Get existing group evaluation comments
     */
    public List<String> getGroupEvaluationComments(Long supervisorId, Long groupId, Long assessmentId) {
        // TODO: Implement fetching existing comments for group evaluation
        return new ArrayList<>();
    }
    
    /**
     * Save group evaluation (same marks for all students in group)
     */
    @Transactional
    public void saveGroupEvaluation(IndustrialSupervisor supervisor, Group group, 
                                    Assessment assessment, Map<String, String> params) {
        // TODO: Implement group evaluation saving
        // Parse params for rubric ratings and comments
        // Apply same marks to all students in the group
    }
    
    /**
     * Check evaluation status for all students
     */
    public Map<Long, Boolean> getStudentEvaluationStatus(Long supervisorId, Long groupId, 
                                                          Long assessmentId, List<Student> students) {
        // TODO: Implement checking which students have been evaluated
        Map<Long, Boolean> status = new HashMap<>();
        for (Student student : students) {
            status.put(student.getId(), hasStudentEvaluation(supervisorId, student.getId(), assessmentId));
        }
        return status;
    }
    
    /**
     * Check if student evaluation exists
     */
    public boolean hasStudentEvaluation(Long supervisorId, Long studentId, Long assessmentId) {
        // TODO: Implement check for existing student evaluation
        return false;
    }
    
    /**
     * Get existing student evaluation ratings
     */
    public Map<Long, Long> getStudentEvaluationRatings(Long supervisorId, Long studentId, Long assessmentId) {
        // TODO: Implement fetching existing ratings for student
        return new HashMap<>();
    }
    
    /**
     * Get existing student evaluation comments
     */
    public List<String> getStudentEvaluationComments(Long supervisorId, Long studentId, Long assessmentId) {
        // TODO: Implement fetching existing comments for student
        return new ArrayList<>();
    }
    
    /**
     * Save individual student evaluation
     */
    @Transactional
    public void saveStudentEvaluation(IndustrialSupervisor supervisor, Student student, 
                                      Assessment assessment, Map<String, String> params) {
        // TODO: Implement student evaluation saving
        // Parse params for rubric ratings and comments
        // Save marks only for this specific student
    }
}