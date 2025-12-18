package com.capstone.adproject.dto;

import java.util.HashMap;
import java.util.Map;

public class StudentRowDto {
    private Long studentId;
    private String studentEmail;
    private String studentName;
    private String groupName;
    
    // Evaluator information
    private Long evaluatorId;
    private String evaluatorEmail;
    private String evaluatorName;
    
    // Rubric ratings: rubricId -> rating name
    private Map<Long, String> groupRubricRatings;
    private Map<Long, String> individualRubricRatings;
    
    // Rubric marks: rubricId -> mark value
    private Map<Long, Double> groupRubricMarks;
    private Map<Long, Double> individualRubricMarks;
    
    // CLO totals: clo number -> SClonWrm (weighted)
    private Map<Integer, Double> cloTotals;
    
    // Total marks for this assessment
    private Double totalMarks;
    
    // ✅ NEW: Separate comments for group and individual
    private String groupComments;
    private String individualComments;
    
    public StudentRowDto() {
        this.groupRubricRatings = new HashMap<>();
        this.individualRubricRatings = new HashMap<>();
        this.groupRubricMarks = new HashMap<>();
        this.individualRubricMarks = new HashMap<>();
        this.cloTotals = new HashMap<>();
    }
    
    // Getters and Setters
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    
    public String getStudentEmail() { return studentEmail; }
    public void setStudentEmail(String studentEmail) { this.studentEmail = studentEmail; }
    
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    
    public Long getEvaluatorId() { return evaluatorId; }
    public void setEvaluatorId(Long evaluatorId) { this.evaluatorId = evaluatorId; }
    
    public String getEvaluatorEmail() { return evaluatorEmail; }
    public void setEvaluatorEmail(String evaluatorEmail) { this.evaluatorEmail = evaluatorEmail; }
    
    public String getEvaluatorName() { return evaluatorName; }
    public void setEvaluatorName(String evaluatorName) { this.evaluatorName = evaluatorName; }
    
    public Map<Long, String> getGroupRubricRatings() { return groupRubricRatings; }
    public void setGroupRubricRatings(Map<Long, String> groupRubricRatings) { 
        this.groupRubricRatings = groupRubricRatings; 
    }
    
    public Map<Long, String> getIndividualRubricRatings() { return individualRubricRatings; }
    public void setIndividualRubricRatings(Map<Long, String> individualRubricRatings) { 
        this.individualRubricRatings = individualRubricRatings; 
    }
    
    public Map<Long, Double> getGroupRubricMarks() { return groupRubricMarks; }
    public void setGroupRubricMarks(Map<Long, Double> groupRubricMarks) { 
        this.groupRubricMarks = groupRubricMarks; 
    }
    
    public Map<Long, Double> getIndividualRubricMarks() { return individualRubricMarks; }
    public void setIndividualRubricMarks(Map<Long, Double> individualRubricMarks) { 
        this.individualRubricMarks = individualRubricMarks; 
    }
    
    public Map<Integer, Double> getCloTotals() { return cloTotals; }
    public void setCloTotals(Map<Integer, Double> cloTotals) { this.cloTotals = cloTotals; }
    
    public Double getTotalMarks() { return totalMarks; }
    public void setTotalMarks(Double totalMarks) { this.totalMarks = totalMarks; }
    
    public String getGroupComments() { return groupComments; }
    public void setGroupComments(String groupComments) { this.groupComments = groupComments; }
    
    public String getIndividualComments() { return individualComments; }
    public void setIndividualComments(String individualComments) { this.individualComments = individualComments; }
}