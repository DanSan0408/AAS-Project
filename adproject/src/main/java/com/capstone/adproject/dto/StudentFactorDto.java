package com.capstone.adproject.dto;

import java.util.ArrayList;
import java.util.List;

public class StudentFactorDto {
    private Long studentId;
    private String studentName;
    private List<Double> peerRatings; // Rate 1, Rate 2, etc.
    private Double individualAverage;
    private Double teamAverage;
    private Double factor;
    private Double cappedFactor; // Factor capped at 1.05
    
    public StudentFactorDto() {
        this.peerRatings = new ArrayList<>();
    }
    
    // Getters and Setters
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    
    public List<Double> getPeerRatings() { return peerRatings; }
    public void setPeerRatings(List<Double> peerRatings) { this.peerRatings = peerRatings; }
    
    public Double getIndividualAverage() { return individualAverage; }
    public void setIndividualAverage(Double individualAverage) { this.individualAverage = individualAverage; }
    
    public Double getTeamAverage() { return teamAverage; }
    public void setTeamAverage(Double teamAverage) { this.teamAverage = teamAverage; }
    
    public Double getFactor() { return factor; }
    public void setFactor(Double factor) { this.factor = factor; }
    
    public Double getCappedFactor() { return cappedFactor; }
    public void setCappedFactor(Double cappedFactor) { this.cappedFactor = cappedFactor; }
}