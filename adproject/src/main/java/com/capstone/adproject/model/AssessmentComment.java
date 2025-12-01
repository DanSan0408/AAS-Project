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
import jakarta.persistence.Table;

/**
 * Entity to store overall comments for assessments
 * Separate from individual rubric/criteria marks
 */
@Entity
@Table(name = "assessment_comments")
public class AssessmentComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Student being evaluated
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluated_student_id", nullable = false)
    private Student evaluatedStudent;

    // Person giving the comment (can be Student, Lecturer, or Supervisor)
    @Column(name = "evaluator_id", nullable = false)
    private Long evaluatorId;

    // Type of evaluator (STUDENT, LECTURER, SUPERVISOR)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvaluatorType evaluatorType;

    // Name of evaluator (for display purposes - lecturers/supervisors show name, students are anonymous)
    @Column(name = "evaluator_name")
    private String evaluatorName;

    // Assessment this comment belongs to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    private Assessment assessment;

    // The overall comment text
    @Column(columnDefinition = "TEXT", nullable = false)
    private String commentText;

    // Assessment type (for context)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommentAssessmentType assessmentType;

    // Timestamp
    @Column(nullable = false)
    private LocalDateTime submittedAt;

    // Anonymous identifier for peer comments (e.g., "Teammate 1", "Teammate 2")
    @Column(name = "anonymous_identifier")
    private String anonymousIdentifier;
    
    // ========== NEW FIELD FOR MULTIPLE COMMENTS ==========
    
    /**
     * Index of the comment (0-based) when multiple comments are required
     * 0 = first comment, 1 = second comment, etc.
     * For backward compatibility, null or 0 represents the "overall" comment
     */
    @Column(name = "comment_index")
    private Integer commentIndex = 0;
    
    /**
     * Label/prompt for this comment (e.g., "Strengths", "Areas for Improvement")
     * Optional - if null, uses default "Comment X"
     */
    @Column(name = "comment_label", length = 500)
    private String commentLabel;

    // Enums
    public enum EvaluatorType {
        STUDENT,
        LECTURER,
        SUPERVISOR
    }

    public enum CommentAssessmentType {
        PEER,
        SELF,
        LECTURER_EVALUATION,
        SUPERVISOR_EVALUATION
    }

    // Constructors
    public AssessmentComment() {
    }

    @PrePersist
    public void prePersist() {
        if (submittedAt == null) {
            submittedAt = LocalDateTime.now();
        }
        if (commentIndex == null) {
            commentIndex = 0;
        }
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

    public Long getEvaluatorId() {
        return evaluatorId;
    }

    public void setEvaluatorId(Long evaluatorId) {
        this.evaluatorId = evaluatorId;
    }

    public EvaluatorType getEvaluatorType() {
        return evaluatorType;
    }

    public void setEvaluatorType(EvaluatorType evaluatorType) {
        this.evaluatorType = evaluatorType;
    }

    public String getEvaluatorName() {
        return evaluatorName;
    }

    public void setEvaluatorName(String evaluatorName) {
        this.evaluatorName = evaluatorName;
    }

    public Assessment getAssessment() {
        return assessment;
    }

    public void setAssessment(Assessment assessment) {
        this.assessment = assessment;
    }

    public String getCommentText() {
        return commentText;
    }

    public void setCommentText(String commentText) {
        this.commentText = commentText;
    }

    public CommentAssessmentType getAssessmentType() {
        return assessmentType;
    }

    public void setAssessmentType(CommentAssessmentType assessmentType) {
        this.assessmentType = assessmentType;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getAnonymousIdentifier() {
        return anonymousIdentifier;
    }

    public void setAnonymousIdentifier(String anonymousIdentifier) {
        this.anonymousIdentifier = anonymousIdentifier;
    }
    
    public Integer getCommentIndex() {
        return commentIndex != null ? commentIndex : 0;
    }
    
    public void setCommentIndex(Integer commentIndex) {
        this.commentIndex = commentIndex;
    }
    
    public String getCommentLabel() {
        return commentLabel;
    }
    
    public void setCommentLabel(String commentLabel) {
        this.commentLabel = commentLabel;
    }

    /**
     * Get display name for the evaluator
     * - Students: Anonymous (Teammate 1, Teammate 2, or "You" for self) if assessment configured as anonymous
     * - Lecturers/Supervisors: Show actual name
     */
    public String getDisplayName() {
        if (evaluatorType == EvaluatorType.STUDENT) {
            // Check if assessment is configured for anonymous comments
            if (assessment != null && !assessment.getCommentsAnonymous()) {
                // Show real name if not anonymous
                return evaluatorName != null ? evaluatorName : "Student";
            }
            // Otherwise show anonymous identifier
            return anonymousIdentifier != null ? anonymousIdentifier : "Teammate";
        } else {
            return evaluatorName != null ? evaluatorName : "Evaluator";
        }
    }
    
    /**
     * Get the display label for this comment
     */
    public String getDisplayLabel() {
        if (commentLabel != null && !commentLabel.trim().isEmpty()) {
            return commentLabel;
        }
        return "Comment " + (getCommentIndex() + 1);
    }
}