package com.capstone.adproject.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.capstone.adproject.model.Rubric;

/**
 * DTO for rubric-specific calculations
 */
public class RubricCalculationDto {
    
    private Rubric rubric;
    private Double rubricFactor; // Rf
    private Double evaluatedRubricMark; // Mr (unweighted)
    private Double weightedRubricMark; // Wr = f × Mr (weighted for Group Assessment)
    private Integer clo;
    private String assessmentType; // "Group Assessment" or "Individual Assessment"
    
    // Evaluator details for display
    private List<Map<String, Object>> evaluatorDetails = new ArrayList<>();
    
    // Getters and Setters
    
    public Rubric getRubric() {
        return rubric;
    }
    
    public void setRubric(Rubric rubric) {
        this.rubric = rubric;
    }
    
    public Double getRubricFactor() {
        return rubricFactor != null ? rubricFactor : 0.0;
    }
    
    public void setRubricFactor(Double rubricFactor) {
        this.rubricFactor = rubricFactor;
    }
    
    public Double getEvaluatedRubricMark() {
        return evaluatedRubricMark != null ? evaluatedRubricMark : 0.0;
    }
    
    public void setEvaluatedRubricMark(Double evaluatedRubricMark) {
        this.evaluatedRubricMark = evaluatedRubricMark;
    }
    
    // NEW: Weighted rubric mark
    public Double getWeightedRubricMark() {
        return weightedRubricMark != null ? weightedRubricMark : 0.0;
    }
    
    public void setWeightedRubricMark(Double weightedRubricMark) {
        this.weightedRubricMark = weightedRubricMark;
    }
    
    public Integer getClo() {
        return clo != null ? clo : 0;
    }
    
    public void setClo(Integer clo) {
        this.clo = clo;
    }
    
    public String getAssessmentType() {
        return assessmentType;
    }
    
    public void setAssessmentType(String assessmentType) {
        this.assessmentType = assessmentType;
    }
    
    public List<Map<String, Object>> getEvaluatorDetails() {
        return evaluatorDetails;
    }
    
    public void setEvaluatorDetails(List<Map<String, Object>> evaluatorDetails) {
        this.evaluatorDetails = evaluatorDetails;
    }
}