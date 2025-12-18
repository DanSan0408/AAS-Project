package com.capstone.adproject.dto;

public class RubricHeaderDto {
    private Long rubricId;
    private String rubricName;
    private Integer clo;
    private String assessmentType;
    
    // Getters and Setters
    public Long getRubricId() { return rubricId; }
    public void setRubricId(Long rubricId) { this.rubricId = rubricId; }
    
    public String getRubricName() { return rubricName; }
    public void setRubricName(String rubricName) { this.rubricName = rubricName; }
    
    public Integer getClo() { return clo; }
    public void setClo(Integer clo) { this.clo = clo; }
    
    public String getAssessmentType() { return assessmentType; }
    public void setAssessmentType(String assessmentType) { this.assessmentType = assessmentType; }
    
    public String getDisplayName() {
        if (clo != null) {
            return rubricName + " (CLO " + clo + ")";
        }
        return rubricName;
    }
}