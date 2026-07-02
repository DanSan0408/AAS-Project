package com.capstone.adproject.dto;

import java.util.Date;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class AssessmentAssignmentDto {
    
    @NotNull
    private Long assessmentId;
    private Long deadlineId;

    @NotBlank
    private String title;
    private String assessorType;
    private String openType; 
    @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd")
    private java.time.LocalDate openDateOnly;
    
    @org.springframework.format.annotation.DateTimeFormat(pattern = "HH:mm")
    private java.time.LocalTime openTimeOnly;
    
    @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd")
    private java.time.LocalDate endDateOnly; 
    
    @org.springframework.format.annotation.DateTimeFormat(pattern = "HH:mm")
    private java.time.LocalTime endTimeOnly;

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

    public java.time.LocalDate getOpenDateOnly() {
        return openDateOnly;
    }

    public void setOpenDateOnly(java.time.LocalDate openDateOnly) {
        this.openDateOnly = openDateOnly;
    }

    public java.time.LocalTime getOpenTimeOnly() {
        return openTimeOnly;
    }

    public void setOpenTimeOnly(java.time.LocalTime openTimeOnly) {
        this.openTimeOnly = openTimeOnly;
    }

    public java.time.LocalDate getEndDateOnly() {
        return endDateOnly;
    }

    public void setEndDateOnly(java.time.LocalDate endDateOnly) {
        this.endDateOnly = endDateOnly;
    }

    public java.time.LocalTime getEndTimeOnly() {
        return endTimeOnly;
    }

    public void setEndTimeOnly(java.time.LocalTime endTimeOnly) {
        this.endTimeOnly = endTimeOnly;
    }
}