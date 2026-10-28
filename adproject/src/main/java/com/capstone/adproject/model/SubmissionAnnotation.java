package com.capstone.adproject.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "submission_annotations", indexes = {
    @Index(name = "idx_annotation_submission", columnList = "submission_id"),
    @Index(name = "idx_annotation_lecturer", columnList = "lecturer_id"),
    @Index(name = "idx_annotation_page", columnList = "submission_id, page_number")
})
public class SubmissionAnnotation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private AssessmentSubmission submission;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lecturer_id", nullable = false)
    private Lecturer lecturer;

    @Column(name = "page_number", nullable = false)
    private Integer pageNumber = 1;

    @Column(name = "strokes_json", columnDefinition = "LONGTEXT")
    private String strokesJson;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public SubmissionAnnotation() {
    }

    @PrePersist
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        if (this.pageNumber == null) {
            this.pageNumber = 1;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @JsonIgnore
    public AssessmentSubmission getSubmission() { return submission; }
    public void setSubmission(AssessmentSubmission submission) { this.submission = submission; }

    @Transient
    @JsonProperty("submissionId")
    public Long getSubmissionId() {
        return submission != null ? submission.getId() : null;
    }

    @JsonIgnore
    public Lecturer getLecturer() { return lecturer; }
    public void setLecturer(Lecturer lecturer) { this.lecturer = lecturer; }

    @Transient
    @JsonProperty("lecturerId")
    public Long getLecturerId() {
        return lecturer != null ? lecturer.getId() : null;
    }

    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }

    public String getStrokesJson() { return strokesJson; }
    public void setStrokesJson(String strokesJson) { this.strokesJson = strokesJson; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

