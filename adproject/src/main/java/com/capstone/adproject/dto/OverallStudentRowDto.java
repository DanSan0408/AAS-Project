package com.capstone.adproject.dto;

import java.util.*;

public class OverallStudentRowDto {
    private Long studentId;
    private String studentName;
    private String groupName;
    private Double factor;
    
    // Assessment results: assessmentId -> result details
    private Map<Long, AssessmentResultDetails> assessmentResults;
    
    // Grand total (Gt)
    private Double grandTotal;
    
    public OverallStudentRowDto() {
        this.assessmentResults = new HashMap<>();
        this.grandTotal = 0.0;
    }
    
    // Getters and Setters
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    
    public Double getFactor() { return factor; }
    public void setFactor(Double factor) { this.factor = factor; }
    
    public Map<Long, AssessmentResultDetails> getAssessmentResults() { return assessmentResults; }
    public void setAssessmentResults(Map<Long, AssessmentResultDetails> assessmentResults) { 
        this.assessmentResults = assessmentResults; 
    }
    
    public Double getGrandTotal() { return grandTotal; }
    public void setGrandTotal(Double grandTotal) { this.grandTotal = grandTotal; }
}