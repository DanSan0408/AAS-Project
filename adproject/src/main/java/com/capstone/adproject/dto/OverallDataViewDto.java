package com.capstone.adproject.dto;

import java.util.*;

public class OverallDataViewDto {
    private List<OverallStudentRowDto> studentRows;
    private List<AssessmentColumnDto> assessmentColumns;
    
    public OverallDataViewDto() {
        this.studentRows = new ArrayList<>();
        this.assessmentColumns = new ArrayList<>();
    }
    
    // Getters and Setters
    public List<OverallStudentRowDto> getStudentRows() { return studentRows; }
    public void setStudentRows(List<OverallStudentRowDto> studentRows) { 
        this.studentRows = studentRows; 
    }
    
    public List<AssessmentColumnDto> getAssessmentColumns() { return assessmentColumns; }
    public void setAssessmentColumns(List<AssessmentColumnDto> assessmentColumns) { 
        this.assessmentColumns = assessmentColumns; 
    }
}