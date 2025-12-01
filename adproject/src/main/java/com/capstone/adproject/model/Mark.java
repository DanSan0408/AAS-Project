package com.capstone.adproject.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "marks")
public class Mark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Student being evaluated
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluated_student_id", nullable = false)
    private Student evaluatedStudent;

    // Student doing the evaluation (for lecturer marks, same as evaluated)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluator_student_id", nullable = false)
    private Student evaluatorStudent;

    // Assessment this mark belongs to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    private Assessment assessment;

    // Rubric this mark is for
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rubric_id")
    private Rubric rubric;

    // SubRubric if applicable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_rubric_id")
    private SubRubric subRubric;

    // Rating selected
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rating_id")
    private Rating rating;

    // The actual mark value
    @Column(nullable = false)
    private Double markValue;

    // CLO related fields
    @Column(nullable = false)
    private Integer clo;

    @Column(nullable = false)
    private Double cloMarks;

    // Assessment type from rubric (Group Assessment or Individual Assessment)
    @Column(name = "assessment_type", nullable = false)
    private String assessmentType;

    // Timestamp
    @Column(nullable = false)
    private LocalDateTime submittedAt;

    // Comments (optional)
    @Column(columnDefinition = "TEXT")
    private String comments;

    // Status of submission
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubmissionStatus status = SubmissionStatus.DRAFT;

    // Enum
    public enum SubmissionStatus {
        DRAFT,
        SUBMITTED,
        FINAL
    }

    // Constructors
    public Mark() {
        this.status = SubmissionStatus.DRAFT;
        this.submittedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Student getEvaluatedStudent() { return evaluatedStudent; }
    public void setEvaluatedStudent(Student evaluatedStudent) { this.evaluatedStudent = evaluatedStudent; }

    public Student getEvaluatorStudent() { return evaluatorStudent; }
    public void setEvaluatorStudent(Student evaluatorStudent) { this.evaluatorStudent = evaluatorStudent; }

    public Assessment getAssessment() { return assessment; }
    public void setAssessment(Assessment assessment) { this.assessment = assessment; }

    public Rubric getRubric() { return rubric; }
    public void setRubric(Rubric rubric) { this.rubric = rubric; }

    public SubRubric getSubRubric() { return subRubric; }
    public void setSubRubric(SubRubric subRubric) { this.subRubric = subRubric; }

    public Rating getRating() { return rating; }
    public void setRating(Rating rating) { this.rating = rating; }

    public Double getMarkValue() { return markValue; }
    public void setMarkValue(Double markValue) { this.markValue = markValue; }

    public Integer getClo() { return clo; }
    public void setClo(Integer clo) { this.clo = clo; }

    public Double getCloMarks() { return cloMarks; }
    public void setCloMarks(Double cloMarks) { this.cloMarks = cloMarks; }

    public String getAssessmentType() { return assessmentType; }
    public void setAssessmentType(String assessmentType) { this.assessmentType = assessmentType; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }

    public SubmissionStatus getStatus() { return status; }
    public void setStatus(SubmissionStatus status) { this.status = status; }

    // Calculate mark value based on rating marks
    public void calculateMarkValue() {
        if (rating != null && rating.getMarks() != null) {
            this.markValue = rating.getMarks().doubleValue();
        }
    }

    // Calculate CLO marks
    public void calculateCloMarks() {
        if (rubric != null && rubric.getCloMarks() != null && rating != null && rating.getMarks() != null) {
            // Proportional CLO marks based on rating
            double proportion = rating.getMarks().doubleValue() / 
                (rubric.getMarks() != null ? rubric.getMarks().doubleValue() : 1.0);
            this.cloMarks = proportion * rubric.getCloMarks();
        }
    }

    @PrePersist
    @PreUpdate
    public void prePersistOrUpdate() {
        if (submittedAt == null) {
            submittedAt = LocalDateTime.now();
        }
        
        calculateMarkValue(); 
        calculateCloMarks();
        
        // Set CLO from rubric
        if (this.clo == null && rubric != null && rubric.getClo() != null) {
            this.clo = rubric.getClo();
        }
        if (this.clo == null) {
            this.clo = 0;
        }
        
        // Set assessment type from rubric
        if (this.assessmentType == null && rubric != null && rubric.getAssessmentTypes() != null) {
            this.assessmentType = rubric.getAssessmentTypes();
        }
        if (this.assessmentType == null) {
            this.assessmentType = "Peer Assessment";
        }
        
        // Ensure mandatory fields are not null
        if (this.markValue == null) {
            this.markValue = 0.0;
        }
        if (this.cloMarks == null) {
            this.cloMarks = 0.0;
        }
    }
}