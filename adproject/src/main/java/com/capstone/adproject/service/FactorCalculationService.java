package com.capstone.adproject.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.capstone.adproject.dto.GroupFactorDto;
import com.capstone.adproject.dto.StudentFactorDto;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Mark;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.MarkRepository;
import com.capstone.adproject.repositories.StudentRepository;

@Service
public class FactorCalculationService {
    
    private static final double FACTOR_CAP = 1.05;
    
    @Autowired
    private MarkRepository markRepository;
    
    @Autowired
    private GroupRepository groupRepository;
    
    @Autowired
    private StudentRepository studentRepository;
    
    /**
     * Calculate factors for all groups based on peer/self assessment data
     * This aggregates data from all peer and self assessments
     */
    public List<GroupFactorDto> calculateFactorsForAllGroups() {
        List<GroupFactorDto> groupFactors = new ArrayList<>();
        
        // Get all groups
        List<Group> groups = groupRepository.findAll();
        
        for (Group group : groups) {
            List<Student> groupMembers = studentRepository.findByGroup(group);
            
            if (groupMembers.isEmpty()) {
                continue; // Skip empty groups
            }
            
            GroupFactorDto groupFactorDto = calculateFactorsForGroup(group, groupMembers);
            if (groupFactorDto != null && !groupFactorDto.getStudentFactors().isEmpty()) {
                groupFactors.add(groupFactorDto);
            }
        }
        
        return groupFactors;
    }
    
    /**
     * Calculate factors for a single group based on peer/self assessment marks
     */
    private GroupFactorDto calculateFactorsForGroup(Group group, List<Student> groupMembers) {
        GroupFactorDto groupFactorDto = new GroupFactorDto();
        groupFactorDto.setGroupId(group.getId());
        groupFactorDto.setGroupName(group.getGroupName());
        groupFactorDto.setTeamSize(groupMembers.size());
        
        List<StudentFactorDto> studentFactors = new ArrayList<>();
        
        // Step 1: Calculate individual averages for each student
        Map<Long, Double> individualAverages = new HashMap<>();
        
        for (Student evaluatedStudent : groupMembers) {
            StudentFactorDto studentFactorDto = new StudentFactorDto();
            studentFactorDto.setStudentId(evaluatedStudent.getId());
            studentFactorDto.setStudentName(evaluatedStudent.getUsername());
            
            // Get all peer/self assessment ratings this student received
            List<Double> ratingsReceived = new ArrayList<>();
            
            for (Student evaluator : groupMembers) {
                // ✅ FIXED: Get ALL marks between evaluator and evaluated student
                List<Mark> allMarks = markRepository.findByEvaluatorStudentAndEvaluatedStudent(
                    evaluator, 
                    evaluatedStudent
                );
                
                // Filter for Peer Assessment and Self Assessment only
                List<Mark> marks = allMarks.stream()
                    .filter(m -> m.getAssessmentType() != null)
                    .filter(m -> "Peer Assessment".equalsIgnoreCase(m.getAssessmentType()) || 
                                "Self Assessment".equalsIgnoreCase(m.getAssessmentType()))
                    .collect(Collectors.toList());
                
                if (!marks.isEmpty()) {
                    // Calculate average rating from this evaluator
                    double evaluatorAverage = marks.stream()
                        .mapToDouble(Mark::getMarkValue)
                        .average()
                        .orElse(0.0);
                    ratingsReceived.add(evaluatorAverage);
                }
            }
            
            // Only include students who have received ratings
            if (ratingsReceived.isEmpty()) {
                continue;
            }
            
            studentFactorDto.setPeerRatings(ratingsReceived);
            
            // Calculate individual average (average of all ratings received)
            double individualAverage = ratingsReceived.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
            
            individualAverage = round(individualAverage, 2);
            studentFactorDto.setIndividualAverage(individualAverage);
            individualAverages.put(evaluatedStudent.getId(), individualAverage);
            
            studentFactors.add(studentFactorDto);
        }
        
        // If no students have ratings, return null
        if (studentFactors.isEmpty()) {
            return null;
        }
        
        // Step 2: Calculate team average
        double teamAverage = individualAverages.values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
        teamAverage = round(teamAverage, 2);
        
        // Step 3: Calculate factors for each student
        for (StudentFactorDto studentFactorDto : studentFactors) {
            studentFactorDto.setTeamAverage(teamAverage);
            
            double factor = 0.0;
            if (teamAverage > 0) {
                factor = studentFactorDto.getIndividualAverage() / teamAverage;
                factor = round(factor, 2);
            }
            
            studentFactorDto.setFactor(factor);
            
            // Cap factor at 1.05
            double cappedFactor = Math.min(factor, FACTOR_CAP);
            cappedFactor = round(cappedFactor, 2);
            studentFactorDto.setCappedFactor(cappedFactor);
        }
        
        groupFactorDto.setStudentFactors(studentFactors);
        return groupFactorDto;
    }
    
    /**
     * Helper method to round to specified decimal places
     */
    private double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}