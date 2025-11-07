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

    // Student doing the evaluation
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluator_student_id", nullable = false)
    private Student evaluatorStudent;

    // Assessment this mark belongs to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    private Assessment assessment;

    // ⭐ UPDATED: Rubric is now OPTIONAL (null for criteria-based marks)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rubric_id")
    private Rubric rubric;

    // SubRubric if applicable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_rubric_id")
    private SubRubric subRubric;

    // ⭐ NEW: Criteria (null for rubric-based marks)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criteria_id")
    private Criteria criteria;

    // ⭐ UPDATED: Rating is OPTIONAL (null for criteria which use ratingLevel directly)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rating_id")
    private Rating rating;

    // ⭐ NEW: Rating level (0-4) for criteria-based marks
    @Column(name = "rating_level")
    private Integer ratingLevel;

    // The actual mark value (calculated from rating level * marks / 4)
    @Column(nullable = false)
    private Double markValue;

    // CLO related fields
    @Column(nullable = false)
    private Integer clo;

    @Column(nullable = false)
    private Double cloMarks;

    // Type of assessment (PEER or SELF)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssessmentType assessmentType;

    // Evaluation type from rubric/criteria (Individual/Group)
    @Column(nullable = false)
    private String evaluationType;

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

    // Enums
    public enum AssessmentType {
        PEER,
        SELF
    }

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
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Student getEvaluatedStudent() {
        return evaluatedStudent;
    }

    public void setEvaluatedStudent(Student evaluatedStudent) {
        this.evaluatedStudent = evaluatedStudent;
    }

    public Student getEvaluatorStudent() {
        return evaluatorStudent;
    }

    public void setEvaluatorStudent(Student evaluatorStudent) {
        this.evaluatorStudent = evaluatorStudent;
    }

    public Assessment getAssessment() {
        return assessment;
    }

    public void setAssessment(Assessment assessment) {
        this.assessment = assessment;
    }

    public Rubric getRubric() {
        return rubric;
    }

    public void setRubric(Rubric rubric) {
        this.rubric = rubric;
    }

    public SubRubric getSubRubric() {
        return subRubric;
    }

    public void setSubRubric(SubRubric subRubric) {
        this.subRubric = subRubric;
    }

    public Criteria getCriteria() {
        return criteria;
    }

    public void setCriteria(Criteria criteria) {
        this.criteria = criteria;
    }

    public Rating getRating() {
        return rating;
    }

    public void setRating(Rating rating) {
        this.rating = rating;
    }

    public Integer getRatingLevel() {
        return ratingLevel;
    }

    public void setRatingLevel(Integer ratingLevel) {
        this.ratingLevel = ratingLevel;
    }

    public Double getMarkValue() {
        return markValue;
    }

    public void setMarkValue(Double markValue) {
        this.markValue = markValue;
    }

    public Integer getClo() {
        return clo;
    }

    public void setClo(Integer clo) {
        this.clo = clo;
    }

    public Double getCloMarks() {
        return cloMarks;
    }

    public void setCloMarks(Double cloMarks) {
        this.cloMarks = cloMarks;
    }

    public AssessmentType getAssessmentType() {
        return assessmentType;
    }

    public void setAssessmentType(AssessmentType assessmentType) {
        this.assessmentType = assessmentType;
    }

    public String getEvaluationType() {
        return evaluationType;
    }

    public void setEvaluationType(String evaluationType) {
        this.evaluationType = evaluationType;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public SubmissionStatus getStatus() {
        return status;
    }

    public void setStatus(SubmissionStatus status) {
        this.status = status;
    }

    // ⭐ UPDATED: Helper methods to check mark type
    public boolean isRubricBased() {
        return rubric != null;
    }

    public boolean isCriteriaBased() {
        return criteria != null;
    }

    // ⭐ UPDATED: Calculate mark value for both rubric and criteria
    public void calculateMarkValue() {
        if (isRubricBased() && rating != null && rubric.getMarks() != null) {
            // Rubric-based: use rating level from Rating entity
            this.markValue = (rating.getLevel() / 4.0) * rubric.getMarks().doubleValue();
        } else if (isCriteriaBased() && ratingLevel != null && criteria.getMarks() != null) {
            // Criteria-based: use ratingLevel field directly
            this.markValue = (ratingLevel / 4.0) * criteria.getMarks().doubleValue();
        }
    }

    // ⭐ UPDATED: Calculate CLO marks for both rubric and criteria
    public void calculateCloMarks() {
        if (isRubricBased() && rubric.getCloMarks() != null && rating != null) {
            // Rubric-based CLO calculation
            this.cloMarks = (rating.getLevel() / 4.0) * rubric.getCloMarks();
        } else if (isCriteriaBased() && criteria.getCloMarks() != null && ratingLevel != null) {
            // Criteria-based CLO calculation
            this.cloMarks = (ratingLevel / 4.0) * criteria.getCloMarks();
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
        
        // Set CLO from rubric or criteria
        if (isRubricBased() && rubric.getClo() != null) {
            this.clo = rubric.getClo();
        } else if (isCriteriaBased() && criteria.getClo() != null) {
            this.clo = criteria.getClo();
        }
        
        // Set evaluation type from rubric or criteria
        if (isRubricBased() && rubric.getEvaluationType() != null) {
            this.evaluationType = rubric.getEvaluationType();
        } else if (isCriteriaBased() && criteria.getEvaluationType() != null) {
            this.evaluationType = criteria.getEvaluationType();
        }
    }
}