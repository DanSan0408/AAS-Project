package com.capstone.adproject.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Mark;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.repositories.MarkRepository;

@Service
public class MarkService {
    
    @Autowired
    private MarkRepository markRepository;
    
    /**
     * Save a single mark
     */
    @Transactional
    public Mark saveMark(Mark mark) {
        return markRepository.save(mark);
    }
    
    /**
     * Save multiple marks at once
     */
    @Transactional
    public List<Mark> saveAllMarks(List<Mark> marks) {
        return markRepository.saveAll(marks);
    }
    
    /**
     * Get all marks for an evaluator and assessment
     */
    public List<Mark> getMarksByEvaluatorAndAssessment(Student evaluator, Assessment assessment) {
        return markRepository.findByEvaluatorStudentAndAssessment(evaluator, assessment);
    }
    
    /**
     * Get marks for a specific student evaluation
     */
    public List<Mark> getMarksForStudentEvaluation(Student evaluator, Student evaluated, Assessment assessment) {
        return markRepository.findByEvaluatorStudentAndEvaluatedStudentAndAssessment(evaluator, evaluated, assessment);
    }
    
    /**
     * Submit peer assessment for a team member
     */
    @Transactional
    public void submitPeerAssessment(Student evaluator, Student evaluated, Assessment assessment, 
                                     Map<Long, Long> rubricRatings, Map<Long, String> comments) {
        
        // Delete existing draft marks if any
        List<Mark> existingMarks = markRepository.findByEvaluatorStudentAndEvaluatedStudentAndAssessment(
                evaluator, evaluated, assessment);
        markRepository.deleteAll(existingMarks);
        
        // Create new marks
        List<Mark> marks = new ArrayList<>();
        
        for (Map.Entry<Long, Long> entry : rubricRatings.entrySet()) {
            Long rubricId = entry.getKey();
            Long ratingId = entry.getValue();
            
            // This assumes you have methods to fetch Rubric and Rating by ID
            // You'll need to inject RubricRepository and RatingRepository
            // For now, creating placeholder mark
            Mark mark = new Mark();
            mark.setEvaluatorStudent(evaluator);
            mark.setEvaluatedStudent(evaluated);
            mark.setAssessment(assessment);
            mark.setAssessmentType(evaluator.getId().equals(evaluated.getId()) ? 
        "Self Assessment" : "Peer Assessment");
            mark.setStatus(Mark.SubmissionStatus.SUBMITTED);
            mark.setSubmittedAt(LocalDateTime.now());
            
            // Set comments if provided
            if (comments != null && comments.containsKey(rubricId)) {
                mark.setComments(comments.get(rubricId));
            }
            
            marks.add(mark);
        }
        
        markRepository.saveAll(marks);
    }
    
    /**
     * Check if evaluator has completed assessments for all team members
     */
    public boolean hasCompletedAllPeerAssessments(Student evaluator, Assessment assessment, List<Student> teamMembers) {
        long completedCount = markRepository.countDistinctEvaluatedStudentsByEvaluatorAndAssessmentAndStatus(
                evaluator, assessment, Mark.SubmissionStatus.SUBMITTED);
        return completedCount >= teamMembers.size();
    }
    
    /**
     * Calculate total marks for a student
     */
    public Double calculateTotalMarks(Student student, Assessment assessment) {
        Double total = markRepository.calculateTotalMarksForStudent(student, assessment);
        return total != null ? total : 0.0;
    }
    
    /**
     * Calculate CLO marks breakdown
     */
    public Map<Integer, Double> calculateCloMarks(Student student, Assessment assessment) {
        List<Object[]> results = markRepository.calculateCloMarksForStudent(student, assessment);
        Map<Integer, Double> cloMarks = new HashMap<>();
        
        for (Object[] result : results) {
            Integer clo = (Integer) result[0];
            Double marks = (Double) result[1];
            cloMarks.put(clo, marks);
        }
        
        return cloMarks;
    }
    
    /**
     * Get assessment progress for a student
     */
    public Map<String, Object> getAssessmentProgress(Student evaluator, Assessment assessment, List<Student> teamMembers) {
        Map<String, Object> progress = new HashMap<>();
        
        // Count completed assessments
        long completedCount = markRepository.countDistinctEvaluatedStudentsByEvaluatorAndAssessmentAndStatus(
                evaluator, assessment, Mark.SubmissionStatus.SUBMITTED);
        
        progress.put("totalMembers", teamMembers.size());
        progress.put("completedAssessments", completedCount);
        progress.put("remainingAssessments", teamMembers.size() - completedCount);
        progress.put("percentageComplete", (completedCount * 100.0) / teamMembers.size());
        
        // Get list of evaluated and unevaluated members
        List<Mark> submittedMarks = markRepository.findByEvaluatorStudentAndAssessmentAndStatus(
                evaluator, assessment, Mark.SubmissionStatus.SUBMITTED);
        
        Set<Long> evaluatedIds = submittedMarks.stream()
                .map(m -> m.getEvaluatedStudent().getId())
                .collect(Collectors.toSet());
        
        List<Student> evaluatedMembers = teamMembers.stream()
                .filter(s -> evaluatedIds.contains(s.getId()))
                .collect(Collectors.toList());
        
        List<Student> unevaluatedMembers = teamMembers.stream()
                .filter(s -> !evaluatedIds.contains(s.getId()))
                .collect(Collectors.toList());
        
        progress.put("evaluatedMembers", evaluatedMembers);
        progress.put("unevaluatedMembers", unevaluatedMembers);
        
        return progress;
    }
    
    /**
     * Finalize all submitted marks (convert from SUBMITTED to FINAL)
     */
    @Transactional
    public void finalizeMarks(Assessment assessment) {
        List<Mark> submittedMarks = markRepository.findByAssessmentAndStatus(assessment, Mark.SubmissionStatus.SUBMITTED);
        for (Mark mark : submittedMarks) {
            mark.setStatus(Mark.SubmissionStatus.FINAL);
        }
        markRepository.saveAll(submittedMarks);
    }
}