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

    // Rubric (optional - null for criteria-based marks)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rubric_id")
    private Rubric rubric;

    // SubRubric if applicable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_rubric_id")
    private SubRubric subRubric;

    // Criteria (null for rubric-based marks)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criteria_id")
    private Criteria criteria;

    // Rating (optional - null for criteria which use ratingLevel directly)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rating_id")
    private Rating rating;

    // Rating level (0-4) for criteria-based marks
    @Column(name = "rating_level")
    private Integer ratingLevel;

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

    // Evaluation type from rubric (Group Evaluation or Individual Evaluation)
    @Column(name = "evaluation_type", nullable = false)
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

    public String getAssessmentType() {
        return assessmentType;
    }

    public void setAssessmentType(String assessmentType) {
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

    // Helper methods
    public boolean isRubricBased() {
        return rubric != null;
    }

    public boolean isCriteriaBased() {
        return criteria != null;
    }

    // Calculate mark value for both rubric and criteria
    public void calculateMarkValue() {
        if (isRubricBased() && rating != null && rubric.getMarks() != null) {
            this.markValue = (rating.getLevel() / 4.0) * rubric.getMarks().doubleValue();
        } else if (isCriteriaBased() && ratingLevel != null && criteria.getMarks() != null) {
            this.markValue = (ratingLevel / 4.0) * criteria.getMarks().doubleValue();
        }
    }

    // Calculate CLO marks for both rubric and criteria
    public void calculateCloMarks() {
        if (isRubricBased() && rubric.getCloMarks() != null && rating != null) {
            this.cloMarks = (rating.getLevel() / 4.0) * rubric.getCloMarks();
        } else if (isCriteriaBased() && criteria.getCloMarks() != null && ratingLevel != null) {
            this.cloMarks = (ratingLevel / 4.0) * criteria.getCloMarks();
        }
    }

    // --- In Mark.java ---
// (Only changing the prePersistOrUpdate method)

@PrePersist
@PreUpdate
public void prePersistOrUpdate() {
    if (submittedAt == null) {
        submittedAt = LocalDateTime.now();
    }
    
    // Ensure MarkValue/CloMarks are calculated first.
    calculateMarkValue(); 
    calculateCloMarks();
    
    // --- Determine derived values safely ---
    
    String tempEvaluationType = null;
    Integer tempClo = null;
    String tempAssessmentType = this.assessmentType; // Keep assessmentType set by controller

    if (isRubricBased() && this.rubric != null) {
        // Attempt to get required fields from the eagerly loaded Rubric or Criteria.
        // If the Rubric/Criteria object is available (it should be, since the controller fetched it), 
        // accessing its fields is usually safe, but null checks are vital.
        tempClo = rubric.getClo();
        tempEvaluationType = rubric.getEvaluationType();
        
        // Use Rubric's assessment type if the controller didn't set the basic one
        if (tempAssessmentType == null) {
             tempAssessmentType = rubric.getAssessmentTypes();
        }
    } else if (isCriteriaBased() && this.criteria != null) {
        tempClo = criteria.getClo();
        tempEvaluationType = criteria.getEvaluationType();
        
        // Use Criteria's assessment type if the controller didn't set the basic one
        if (tempAssessmentType == null) {
             tempAssessmentType = criteria.getAssessmentTypes();
        }
    }

    // --- CRITICAL DEFAULTS: If any derived field is null, set a default to prevent DB error ---

    // 1. Set CLO
    if (tempClo != null) {
        this.clo = tempClo;
    } else if (this.clo == null) {
        // Fallback for database requirement. Should be fixed at the Rubric/Criteria data level.
        this.clo = 0; 
    }
    
    // 2. Set Evaluation Type (The field causing the direct error)
    if (tempEvaluationType != null) {
        this.evaluationType = tempEvaluationType;
    } else if (this.evaluationType == null) {
        // Fallback: Since this controller ONLY handles Individual (Peer/Self), this is a safe default.
        this.evaluationType = "Individual"; 
    }
    
    // 3. Set Assessment Type (If not set by controller or component)
    if (tempAssessmentType != null) {
        this.assessmentType = tempAssessmentType;
    } else if (this.assessmentType == null) {
        this.assessmentType = "Peer Assessment"; // Safe default for this context
    }
    
    // 4. Ensure mandatory calculated fields are not null (MarkValue and CloMarks)
    if (this.markValue == null) {
        this.markValue = 0.0;
    }
    if (this.cloMarks == null) {
        this.cloMarks = 0.0;
    }
}
}