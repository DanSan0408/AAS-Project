package com.capstone.adproject.dto;

import java.util.Set;
import java.util.TreeSet;

public class AssessmentColumnDto {
    private Long assessmentId;
    private String assessmentTitle;
    private Set<Integer> clos; // CLOs in this assessment
    
    public AssessmentColumnDto() {
        this.clos = new TreeSet<>();
    }
    
    // Getters and Setters
    public Long getAssessmentId() { return assessmentId; }
    public void setAssessmentId(Long assessmentId) { this.assessmentId = assessmentId; }
    
    public String getAssessmentTitle() { return assessmentTitle; }
    public void setAssessmentTitle(String assessmentTitle) { this.assessmentTitle = assessmentTitle; }
    
    public Set<Integer> getClos() { return clos; }
    public void setClos(Set<Integer> clos) { this.clos = clos; }
}