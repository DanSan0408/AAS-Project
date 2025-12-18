package com.capstone.adproject.dto;

import java.util.*;

public class AssessmentDataViewDto {
    private Long assessmentId;
    private String assessmentTitle;
    
    // Student rows sorted by name A-Z
    private List<StudentRowDto> studentRows;
    
    // Rubric information for table headers
    private List<RubricHeaderDto> groupRubrics;
    private List<RubricHeaderDto> individualRubrics;
    
    // CLOs present in this assessment
    private Set<Integer> clos;
    
    public AssessmentDataViewDto() {
        this.studentRows = new ArrayList<>();
        this.groupRubrics = new ArrayList<>();
        this.individualRubrics = new ArrayList<>();
        this.clos = new TreeSet<>();
    }
    
    // Getters and Setters
    public Long getAssessmentId() { return assessmentId; }
    public void setAssessmentId(Long assessmentId) { this.assessmentId = assessmentId; }
    
    public String getAssessmentTitle() { return assessmentTitle; }
    public void setAssessmentTitle(String assessmentTitle) { this.assessmentTitle = assessmentTitle; }
    
    public List<StudentRowDto> getStudentRows() { return studentRows; }
    public void setStudentRows(List<StudentRowDto> studentRows) { this.studentRows = studentRows; }
    
    public List<RubricHeaderDto> getGroupRubrics() { return groupRubrics; }
    public void setGroupRubrics(List<RubricHeaderDto> groupRubrics) { this.groupRubrics = groupRubrics; }
    
    public List<RubricHeaderDto> getIndividualRubrics() { return individualRubrics; }
    public void setIndividualRubrics(List<RubricHeaderDto> individualRubrics) { 
        this.individualRubrics = individualRubrics; 
    }
    
    public Set<Integer> getClos() { return clos; }
    public void setClos(Set<Integer> clos) { this.clos = clos; }
}