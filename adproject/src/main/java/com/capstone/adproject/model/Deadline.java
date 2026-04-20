package com.capstone.adproject.model;

import java.util.Date;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "deadlines", indexes = {
    @Index(name = "idx_deadline_assessment_assessor", columnList = "assessmentId, assessorType")
})

public class Deadline {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private Date date;
    private Long assessmentId; 
private String assessorType; 
private Date openDate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public Long getAssessmentId() {
    return assessmentId;
}

public void setAssessmentId(Long assessmentId) {
    this.assessmentId = assessmentId;
}

public String getAssessorType() {
    return assessorType;
}

public void setAssessorType(String assessorType) {
    this.assessorType = assessorType;
}

public Date getOpenDate() {
    return openDate;
}

public void setOpenDate(Date openDate) {
    this.openDate = openDate;
}
}