package com.capstone.adproject.dto;

import java.util.HashMap;
import java.util.Map;

public class AssessmentResultDetails {
    private Map<Integer, Double> cloMrm; // SClonMrm
    private Map<Integer, Double> cloWrm; // SClonWrm
    private Double totalT; // T
    
    public AssessmentResultDetails() {
        this.cloMrm = new HashMap<>();
        this.cloWrm = new HashMap<>();
        this.totalT = 0.0;
    }
    
    // Getters and Setters
    public Map<Integer, Double> getCloMrm() { return cloMrm; }
    public void setCloMrm(Map<Integer, Double> cloMrm) { this.cloMrm = cloMrm; }
    
    public Map<Integer, Double> getCloWrm() { return cloWrm; }
    public void setCloWrm(Map<Integer, Double> cloWrm) { this.cloWrm = cloWrm; }
    
    public Double getTotalT() { return totalT; }
    public void setTotalT(Double totalT) { this.totalT = totalT; }
}