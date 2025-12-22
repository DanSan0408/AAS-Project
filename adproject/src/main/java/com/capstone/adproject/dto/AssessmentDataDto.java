package com.capstone.adproject.dto;

import java.util.ArrayList;
import java.util.List;

import com.capstone.adproject.model.Assessment;

/**
 * DTO for complete assessment data view
 */
public class AssessmentDataDto {
    
    private Assessment assessment;
    private List<StudentAssessmentDataDto> studentDataList = new ArrayList<>();
    
    public Assessment getAssessment() {
        return assessment;
    }
    
    public void setAssessment(Assessment assessment) {
        this.assessment = assessment;
    }
    
    public List<StudentAssessmentDataDto> getStudentDataList() {
        return studentDataList;
    }
    
    public void setStudentDataList(List<StudentAssessmentDataDto> studentDataList) {
        this.studentDataList = studentDataList;
    }
}