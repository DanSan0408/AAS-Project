package com.capstone.adproject.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.dto.RubricCalculationDto;
import com.capstone.adproject.dto.StudentAssessmentResultDto;
import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.AssessmentComment;
import com.capstone.adproject.model.CalculatedResult;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.repositories.AssessmentCommentRepository;
import com.capstone.adproject.repositories.AssessmentRepository;
import com.capstone.adproject.repositories.CalculatedResultRepository;
import com.capstone.adproject.repositories.MarkRepository;
import com.capstone.adproject.repositories.RubricRepository;
import com.capstone.adproject.repositories.StudentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class CalculatedResultPersistenceService {
    
    @Autowired
    private CalculatedResultRepository calculatedResultRepository;
    
    @Autowired
    private AssessmentCalculationService assessmentCalculationService;
    
    @Autowired
    private StudentRepository studentRepository;
    
    @Autowired
    private AssessmentRepository assessmentRepository;
    
    @Autowired
    private RubricRepository rubricRepository;
    
    @Autowired
    private MarkRepository markRepository;
    
    @Autowired
    private AssessmentCommentRepository assessmentCommentRepository;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Calculate and persist results for a specific student and assessment
     */
    @Transactional
    public void calculateAndPersistStudentResult(Long studentId, Long assessmentId) {
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        
        Assessment assessment = assessmentRepository.findById(assessmentId)
            .orElseThrow(() -> new IllegalArgumentException("Assessment not found"));
        
        // Delete existing results
        List<CalculatedResult> existing = calculatedResultRepository.findByStudentAndAssessment(student, assessment);
        calculatedResultRepository.deleteAll(existing);
        
        // Calculate new results
        StudentAssessmentResultDto resultDto = assessmentCalculationService.calculateStudentAssessmentResult(studentId, assessmentId);
        
        // Persist individual rubric results
        List<CalculatedResult> resultsToSave = new ArrayList<>();
        
        for (RubricCalculationDto rubricCalc : resultDto.getRubricCalculations()) {
            CalculatedResult result = new CalculatedResult();
            result.setStudent(student);
            result.setAssessment(assessment);
            
            Rubric rubric = rubricRepository.findById(rubricCalc.getRubricId())
                .orElseThrow(() -> new IllegalArgumentException("Rubric not found"));
            result.setRubric(rubric);
            
            result.setStudentFactor(resultDto.getStudentFactor());
            result.setRubricFactor(rubricCalc.getRubricFactor());
            result.setConfiguredRubricMark(rubricCalc.getConfiguredRubricMark());
            result.setEvaluatedRubricMark(rubricCalc.getEvaluatedRubricMark());
            result.setAssessmentType(rubricCalc.getAssessmentType());
            result.setClo(rubricCalc.getClo());
            
            // Set weighted mark for group assessments
            if ("Group Assessment".equalsIgnoreCase(rubricCalc.getAssessmentType())) {
                Double weightedMark = resultDto.getWeightedRubricMarks().get(rubricCalc.getRubricId());
                result.setWeightedRubricMark(weightedMark);
            }
            
            // Determine calculation type
            if (rubric.hasSubRubrics()) {
                result.setCalculationType("SUB_RUBRIC");
            } else {
                result.setCalculationType("DIRECT_RATING");
            }
            
            resultsToSave.add(result);
        }
        
        // Save rubric-level results
        calculatedResultRepository.saveAll(resultsToSave);
        
        // Create and save summary result (with totals)
        CalculatedResult summaryResult = new CalculatedResult();
        summaryResult.setStudent(student);
        summaryResult.setAssessment(assessment);
        summaryResult.setRubric(null); // No specific rubric for summary
        summaryResult.setStudentFactor(resultDto.getStudentFactor());
        summaryResult.setTotalGroupMarks(resultDto.getTotalGroupMarks());
        summaryResult.setTotalIndividualMarks(resultDto.getTotalIndividualMarks());
        summaryResult.setFinalTotalMarks(resultDto.getTotalMarks());
        
        // Convert maps to JSON
        try {
            summaryResult.setCloTotalMarksJson(objectMapper.writeValueAsString(resultDto.getCloTotalMarks()));
            summaryResult.setCloWeightedMarksJson(objectMapper.writeValueAsString(resultDto.getCloWeightedMarks()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        calculatedResultRepository.save(summaryResult);
    }
    
    /**
     * Calculate and persist results for all students in an assessment
     */
    @Transactional
    public void calculateAndPersistAllStudents(Long assessmentId) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
            .orElseThrow(() -> new IllegalArgumentException("Assessment not found"));
        
        // Delete all existing results for this assessment
        calculatedResultRepository.deleteByAssessment(assessment);
        
        // Calculate for all students
        List<Student> allStudents = studentRepository.findAll();
        
        for (Student student : allStudents) {
            try {
                calculateAndPersistStudentResult(student.getId(), assessmentId);
                System.out.println("✓ Calculated for student: " + student.getUsername());
            } catch (Exception e) {
                System.err.println("✗ Error calculating for student " + student.getId() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Retrieve persisted results for a student
     */
    @Transactional(readOnly = true)
    public StudentAssessmentResultDto getPersistedResults(Long studentId, Long assessmentId) {
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        
        Assessment assessment = assessmentRepository.findById(assessmentId)
            .orElseThrow(() -> new IllegalArgumentException("Assessment not found"));
        
        // Get summary result
        CalculatedResult summary = calculatedResultRepository.findSummaryByStudentAndAssessment(student, assessment)
            .orElse(null);
        
        if (summary == null) {
            return null; // No calculated results found
        }
        
        StudentAssessmentResultDto result = new StudentAssessmentResultDto();
        result.setStudentId(studentId);
        result.setStudentName(student.getUsername());
        result.setAssessmentId(assessmentId);
        result.setAssessmentTitle(assessment.getTitle());
        result.setStudentFactor(summary.getStudentFactor());
        result.setTotalGroupMarks(summary.getTotalGroupMarks());
        result.setTotalIndividualMarks(summary.getTotalIndividualMarks());
        result.setTotalMarks(summary.getFinalTotalMarks());
        
        // Parse JSON fields
        try {
            if (summary.getCloTotalMarksJson() != null) {
                result.setCloTotalMarks(objectMapper.readValue(summary.getCloTotalMarksJson(), 
                    objectMapper.getTypeFactory().constructMapType(HashMap.class, Integer.class, Double.class)));
            }
            if (summary.getCloWeightedMarksJson() != null) {
                result.setCloWeightedMarks(objectMapper.readValue(summary.getCloWeightedMarksJson(), 
                    objectMapper.getTypeFactory().constructMapType(HashMap.class, Integer.class, Double.class)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Get individual rubric results
        List<CalculatedResult> rubricResults = calculatedResultRepository.findByStudentAndAssessment(student, assessment).stream()
            .filter(cr -> cr.getRubric() != null)
            .toList();
        
        List<RubricCalculationDto> rubricCalcs = new ArrayList<>();
        for (CalculatedResult cr : rubricResults) {
            RubricCalculationDto calc = new RubricCalculationDto();
            calc.setRubricId(cr.getRubric().getId());
            calc.setRubricName(cr.getRubric().getName());
            calc.setAssessmentType(cr.getAssessmentType());
            calc.setClo(cr.getClo());
            calc.setRubricFactor(cr.getRubricFactor());
            calc.setConfiguredRubricMark(cr.getConfiguredRubricMark());
            calc.setEvaluatedRubricMark(cr.getEvaluatedRubricMark());
            rubricCalcs.add(calc);
            
            if (cr.getWeightedRubricMark() != null) {
                result.getWeightedRubricMarks().put(cr.getRubric().getId(), cr.getWeightedRubricMark());
            }
        }
        
        result.setRubricCalculations(rubricCalcs);
        
        return result;
    }
    
    /**
     * ✅ Get all student results for an assessment (sorted by student name A-Z)
     */
    @Transactional(readOnly = true)
    public List<StudentAssessmentResultDto> getAllResultsForAssessment(Long assessmentId) {
        List<Student> allStudents = studentRepository.findAll();
        
        // Sort students by name (A-Z)
        allStudents.sort(Comparator.comparing(Student::getUsername));
        
        List<StudentAssessmentResultDto> results = new ArrayList<>();
        
        for (Student student : allStudents) {
            try {
                StudentAssessmentResultDto result = getPersistedResults(student.getId(), assessmentId);
                if (result != null) {
                    results.add(result);
                }
            } catch (Exception e) {
                System.err.println("Error getting results for student " + student.getId() + ": " + e.getMessage());
            }
        }
        
        return results;
    }
    
    /**
     * ✅ FIXED: Get ACTUAL comments from AssessmentComment table (not Mark comments)
     */
    @Transactional(readOnly = true)
    public Map<Long, List<String>> getCommentsForStudent(Long studentId, Long assessmentId) {
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        
        Assessment assessment = assessmentRepository.findById(assessmentId)
            .orElseThrow(() -> new IllegalArgumentException("Assessment not found"));
        
        // Get all assessment comments for this student
        List<AssessmentComment> comments = assessmentCommentRepository.findByEvaluatedStudent(student).stream()
            .filter(c -> c.getAssessment().getId().equals(assessmentId))
            .filter(c -> c.getRubricId() == null) // Only assessment-level comments (not rubric-specific)
            .collect(Collectors.toList());
        
        // Group comments by rubric (though rubricId is null for assessment-level)
        // We'll return a map with a single entry for all comments
        Map<Long, List<String>> commentsByRubric = new HashMap<>();
        List<String> allCommentTexts = new ArrayList<>();
        
        for (AssessmentComment comment : comments) {
            String commentText = comment.getCommentText();
            
            // Only include non-empty comments
            if (commentText != null && !commentText.trim().isEmpty()) {
                // Format: "Evaluator: Comment"
                String evaluatorName = comment.getEvaluatorName() != null ? comment.getEvaluatorName() : "Unknown";
                String formattedComment = evaluatorName + ": " + commentText;
                allCommentTexts.add(formattedComment);
            }
        }
        
        if (!allCommentTexts.isEmpty()) {
            commentsByRubric.put(0L, allCommentTexts); // Use 0 as a dummy key
        }
        
        return commentsByRubric;
    }
}