package com.capstone.adproject.dto;

import java.util.HashMap;
import java.util.Map;

public class RubricCalculationDto {
    private Long rubricId;
    private String rubricName;
    private String assessmentType; // "Group Assessment" or "Individual Assessment"
    private Integer clo;
    
    // For sub-rubric calculations
    private Double subRubricFactor; // μ ln
    private Double rubricFactor; // Rf
    private Double configuredRubricMark; // Rm (from rubric.getMarks())
    private Double evaluatedRubricMark; // Mr = Rf × Rm
    
    // For direct rating calculations
    private Double averageRubricFactor; // μ Rf
    
    // Breakdown by lecturer/evaluator
    private Map<Long, Double> evaluatorAverages; // μ srmln per evaluator
    private Map<Long, Double> evaluatorRubricFactors; // Rf per evaluator
    
    public RubricCalculationDto() {
        this.evaluatorAverages = new HashMap<>();
        this.evaluatorRubricFactors = new HashMap<>();
    }
    
    // Getters and Setters
    public Long getRubricId() { return rubricId; }
    public void setRubricId(Long rubricId) { this.rubricId = rubricId; }
    
    public String getRubricName() { return rubricName; }
    public void setRubricName(String rubricName) { this.rubricName = rubricName; }
    
    public String getAssessmentType() { return assessmentType; }
    public void setAssessmentType(String assessmentType) { this.assessmentType = assessmentType; }
    
    public Integer getClo() { return clo; }
    public void setClo(Integer clo) { this.clo = clo; }
    
    public Double getSubRubricFactor() { return subRubricFactor; }
    public void setSubRubricFactor(Double subRubricFactor) { this.subRubricFactor = subRubricFactor; }
    
    public Double getRubricFactor() { return rubricFactor; }
    public void setRubricFactor(Double rubricFactor) { this.rubricFactor = rubricFactor; }
    
    public Double getConfiguredRubricMark() { return configuredRubricMark; }
    public void setConfiguredRubricMark(Double configuredRubricMark) { this.configuredRubricMark = configuredRubricMark; }
    
    public Double getEvaluatedRubricMark() { return evaluatedRubricMark; }
    public void setEvaluatedRubricMark(Double evaluatedRubricMark) { this.evaluatedRubricMark = evaluatedRubricMark; }
    
    public Double getAverageRubricFactor() { return averageRubricFactor; }
    public void setAverageRubricFactor(Double averageRubricFactor) { this.averageRubricFactor = averageRubricFactor; }
    
    public Map<Long, Double> getEvaluatorAverages() { return evaluatorAverages; }
    public void setEvaluatorAverages(Map<Long, Double> evaluatorAverages) { this.evaluatorAverages = evaluatorAverages; }
    
    public Map<Long, Double> getEvaluatorRubricFactors() { return evaluatorRubricFactors; }
    public void setEvaluatorRubricFactors(Map<Long, Double> evaluatorRubricFactors) { this.evaluatorRubricFactors = evaluatorRubricFactors; }
}