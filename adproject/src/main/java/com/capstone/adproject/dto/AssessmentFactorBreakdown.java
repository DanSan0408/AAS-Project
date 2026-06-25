package com.capstone.adproject.dto;

import java.util.List;

public class AssessmentFactorBreakdown {
    private Long assessmentId;
    private String assessmentTitle;
    private List<List<Double>> rawRatings;
    private Double averageRating;
    private Double groupAverage;
    private Double calculatedFactor;
    private Double weightage;

    public AssessmentFactorBreakdown() {}

    public Long getAssessmentId() { return assessmentId; }
    public void setAssessmentId(Long assessmentId) { this.assessmentId = assessmentId; }

    public String getAssessmentTitle() { return assessmentTitle; }
    public void setAssessmentTitle(String assessmentTitle) { this.assessmentTitle = assessmentTitle; }

    public List<List<Double>> getRawRatings() { return rawRatings; }
    public void setRawRatings(List<List<Double>> rawRatings) { this.rawRatings = rawRatings; }

    public Double getAverageRating() { return averageRating; }
    public void setAverageRating(Double averageRating) { this.averageRating = averageRating; }

    public Double getGroupAverage() { return groupAverage; }
    public void setGroupAverage(Double groupAverage) { this.groupAverage = groupAverage; }

    public Double getCalculatedFactor() { return calculatedFactor; }
    public void setCalculatedFactor(Double calculatedFactor) { this.calculatedFactor = calculatedFactor; }

    public Double getWeightage() { return weightage; }
    public void setWeightage(Double weightage) { this.weightage = weightage; }
}
