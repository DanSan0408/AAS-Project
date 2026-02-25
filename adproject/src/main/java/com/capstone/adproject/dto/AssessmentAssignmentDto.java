package com.capstone.adproject.dto;

import java.util.Date;

public class AssessmentAssignmentDto {
    
    private Long assessmentId;
    private Long deadlineId;
    private String title;
    private String assessorType;
    private String openType; 
    private Date openDate;
    private Date endDate; 

    public AssessmentAssignmentDto() {
    }

    public Long getAssessmentId() {
        return assessmentId;
    }

    public void setAssessmentId(Long assessmentId) {
        this.assessmentId = assessmentId;
    }

    public Long getDeadlineId() {
        return deadlineId;
    }

    public void setDeadlineId(Long deadlineId) {
        this.deadlineId = deadlineId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAssessorType() {
        return assessorType;
    }

    public void setAssessorType(String assessorType) {
        this.assessorType = assessorType;
    }

    public String getOpenType() {
        return openType;
    }

    public void setOpenType(String openType) {
        this.openType = openType;
    }

    public Date getOpenDate() {
        return openDate;
    }

    public void setOpenDate(Date openDate) {
        this.openDate = openDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }
}