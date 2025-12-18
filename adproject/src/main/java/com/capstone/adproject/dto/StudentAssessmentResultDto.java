package com.capstone.adproject.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StudentAssessmentResultDto {
    private Long studentId;
    private String studentName;
    private Long assessmentId;
    private String assessmentTitle;
    
    // Factor from peer/self assessment
    private Double studentFactor; // f
    
    // Rubric calculations
    private List<RubricCalculationDto> rubricCalculations;
    
    // Totals
    private Double totalGroupMarks; // Tg (sum of all group rubric Mr)
    private Double totalIndividualMarks; // Ts (sum of all individual rubric Mr)
    
    // Weighted calculations (for group assessments only)
    private Map<Long, Double> weightedRubricMarks; // Wr = f × Mr (rubricId -> weighted mark)
    
    // CLO-based totals
    private Map<Integer, Double> cloTotalMarks; // SClonMrm (clo -> sum of Mr)
    private Map<Integer, Double> cloWeightedMarks; // SClonWrm (clo -> sum of Wr + individual Mr)
    
    // Final total
    private Double totalMarks; // T = sum of all SClonWr
    
    public StudentAssessmentResultDto() {
        this.rubricCalculations = new ArrayList<>();
        this.weightedRubricMarks = new HashMap<>();
        this.cloTotalMarks = new HashMap<>();
        this.cloWeightedMarks = new HashMap<>();
    }
    
    // Getters and Setters
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    
    public Long getAssessmentId() { return assessmentId; }
    public void setAssessmentId(Long assessmentId) { this.assessmentId = assessmentId; }
    
    public String getAssessmentTitle() { return assessmentTitle; }
    public void setAssessmentTitle(String assessmentTitle) { this.assessmentTitle = assessmentTitle; }
    
    public Double getStudentFactor() { return studentFactor; }
    public void setStudentFactor(Double studentFactor) { this.studentFactor = studentFactor; }
    
    public List<RubricCalculationDto> getRubricCalculations() { return rubricCalculations; }
    public void setRubricCalculations(List<RubricCalculationDto> rubricCalculations) { 
        this.rubricCalculations = rubricCalculations; 
    }
    
    public Double getTotalGroupMarks() { return totalGroupMarks; }
    public void setTotalGroupMarks(Double totalGroupMarks) { this.totalGroupMarks = totalGroupMarks; }
    
    public Double getTotalIndividualMarks() { return totalIndividualMarks; }
    public void setTotalIndividualMarks(Double totalIndividualMarks) { 
        this.totalIndividualMarks = totalIndividualMarks; 
    }
    
    public Map<Long, Double> getWeightedRubricMarks() { return weightedRubricMarks; }
    public void setWeightedRubricMarks(Map<Long, Double> weightedRubricMarks) { 
        this.weightedRubricMarks = weightedRubricMarks; 
    }
    
    public Map<Integer, Double> getCloTotalMarks() { return cloTotalMarks; }
    public void setCloTotalMarks(Map<Integer, Double> cloTotalMarks) { 
        this.cloTotalMarks = cloTotalMarks; 
    }
    
    public Map<Integer, Double> getCloWeightedMarks() { return cloWeightedMarks; }
    public void setCloWeightedMarks(Map<Integer, Double> cloWeightedMarks) { 
        this.cloWeightedMarks = cloWeightedMarks; 
    }
    
    public Double getTotalMarks() { return totalMarks; }
    public void setTotalMarks(Double totalMarks) { this.totalMarks = totalMarks; }
}