package com.capstone.adproject.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Student;

/**
 * DTO for student's data in a specific assessment
 */
public class StudentAssessmentDataDto {
    
    private Student student;
    private Assessment assessment;
    private Double factor; // For peer/self assessments
    
    // Map of rubricId -> RubricCalculationDto
    private Map<Long, RubricCalculationDto> rubricCalculations = new HashMap<>();
    
    // CLO-based sums
    private Map<Integer, Double> cloSums = new HashMap<>(); // SClonMrm - All rubrics raw
    private Map<Integer, Double> cloWeightedSums = new HashMap<>(); // SClonWrm - Combined weighted
    
    // NEW: Separated CLO sums for display
    private Map<Integer, Double> cloWeightedSumsGroupOnly = new HashMap<>(); // Only Group Assessment rubrics weighted
    private Map<Integer, Double> cloSumsIndividualOnly = new HashMap<>(); // Only Individual Assessment rubrics raw
    
    // Total marks for this assessment
    private Double totalMarks = 0.0; // T
    
    // Comments
    private List<String> groupComments = new ArrayList<>();
    private List<String> individualComments = new ArrayList<>();
    
    private Double totalUnweightedMarks; // Total of Raw Marks
    private Double totalWeightedMarks;   // Total of Weighted Marks
    
    // Getters and Setters
    
    public Student getStudent() {
        return student;
    }
    
    public void setStudent(Student student) {
        this.student = student;
    }
    
    public Assessment getAssessment() {
        return assessment;
    }
    
    public void setAssessment(Assessment assessment) {
        this.assessment = assessment;
    }
    
    public Double getFactor() {
        return factor != null ? factor : 1.0;
    }
    
    public void setFactor(Double factor) {
        this.factor = factor;
    }
    
    public Map<Long, RubricCalculationDto> getRubricCalculations() {
        return rubricCalculations;
    }
    
    public void setRubricCalculations(Map<Long, RubricCalculationDto> rubricCalculations) {
        this.rubricCalculations = rubricCalculations;
    }
    
    public Map<Integer, Double> getCloSums() {
        return cloSums;
    }
    
    public void setCloSums(Map<Integer, Double> cloSums) {
        this.cloSums = cloSums;
    }
    
    public Map<Integer, Double> getCloWeightedSums() {
        return cloWeightedSums;
    }
    
    public void setCloWeightedSums(Map<Integer, Double> cloWeightedSums) {
        this.cloWeightedSums = cloWeightedSums;
    }
    
    // NEW GETTERS/SETTERS FOR SEPARATED SUMS
    
    public Map<Integer, Double> getCloWeightedSumsGroupOnly() {
        return cloWeightedSumsGroupOnly;
    }
    
    public void setCloWeightedSumsGroupOnly(Map<Integer, Double> cloWeightedSumsGroupOnly) {
        this.cloWeightedSumsGroupOnly = cloWeightedSumsGroupOnly;
    }
    
    public Map<Integer, Double> getCloSumsIndividualOnly() {
        return cloSumsIndividualOnly;
    }
    
    public void setCloSumsIndividualOnly(Map<Integer, Double> cloSumsIndividualOnly) {
        this.cloSumsIndividualOnly = cloSumsIndividualOnly;
    }
    
    public Double getTotalMarks() {
        return totalMarks != null ? totalMarks : 0.0;
    }
    
    public void setTotalMarks(Double totalMarks) {
        this.totalMarks = totalMarks;
    }
    
    public List<String> getGroupComments() {
        return groupComments;
    }
    
    public void setGroupComments(List<String> groupComments) {
        this.groupComments = groupComments;
    }
    
    public List<String> getIndividualComments() {
        return individualComments;
    }
    
    public void setIndividualComments(List<String> individualComments) {
        this.individualComments = individualComments;
    }

    public Double getTotalUnweightedMarks() {
        return totalUnweightedMarks != null ? totalUnweightedMarks : 0.0;
    }

    public void setTotalUnweightedMarks(Double totalUnweightedMarks) {
        this.totalUnweightedMarks = totalUnweightedMarks;
    }

    public Double getTotalWeightedMarks() {
        return totalWeightedMarks != null ? totalWeightedMarks : 0.0;
    }

    public void setTotalWeightedMarks(Double totalWeightedMarks) {
        this.totalWeightedMarks = totalWeightedMarks;
    }
}