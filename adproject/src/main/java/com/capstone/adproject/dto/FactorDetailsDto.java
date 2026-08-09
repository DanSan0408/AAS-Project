package com.capstone.adproject.dto;

import java.util.List;

public class FactorDetailsDto {
    private Long studentId;
    private String studentName;
    private String studentEmail;
    
    private List<AssessmentFactorBreakdown> breakdowns;
    
    private Double finalCalculatedFactor;
    private Double currentOverriddenFactor;
    private Boolean isOverridden;

    public FactorDetailsDto() {}

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getStudentEmail() { return studentEmail; }
    public void setStudentEmail(String studentEmail) { this.studentEmail = studentEmail; }

    public List<AssessmentFactorBreakdown> getBreakdowns() { return breakdowns; }
    public void setBreakdowns(List<AssessmentFactorBreakdown> breakdowns) { this.breakdowns = breakdowns; }

    public Double getFinalCalculatedFactor() { return finalCalculatedFactor; }
    public void setFinalCalculatedFactor(Double finalCalculatedFactor) { this.finalCalculatedFactor = finalCalculatedFactor; }

    public Double getCurrentOverriddenFactor() { return currentOverriddenFactor; }
    public void setCurrentOverriddenFactor(Double currentOverriddenFactor) { this.currentOverriddenFactor = currentOverriddenFactor; }

    public Boolean getIsOverridden() { return isOverridden; }
    public void setIsOverridden(Boolean overridden) { isOverridden = overridden; }
}
