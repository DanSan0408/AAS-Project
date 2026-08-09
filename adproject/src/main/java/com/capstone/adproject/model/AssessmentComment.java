package com.capstone.adproject.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;


@Entity
@Table(name = "assessment_comments")
public class AssessmentComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluated_student_id", nullable = false, foreignKey = @ForeignKey(name = "fk_assessment_comments_evaluated_student"))
    private Student evaluatedStudent;

    @Column(name = "evaluator_id", nullable = false)
    private Long evaluatorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvaluatorType evaluatorType;

    @Column(name = "evaluator_name")
    private String evaluatorName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    private Assessment assessment;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String commentText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommentAssessmentType assessmentType;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "anonymous_identifier")
    private String anonymousIdentifier;
    
    @Column(name = "comment_index")
    private Integer commentIndex = 0;
    
    @Column(name = "comment_label", columnDefinition = "TEXT")
    private String commentLabel;
    
    @Column(name = "rubric_assessment_type", length = 50)
    private String rubricAssessmentType;

    
    @Transient
    private String cachedDisplayName;
    
    @Column(name = "rubric_id")
    private Long rubricId;

    public enum EvaluatorType {
        STUDENT,
        LECTURER,
        SUPERVISOR
    }

    public enum CommentAssessmentType {
        PEER,
        SELF,
        TEAM,
        LECTURER_EVALUATION,
        SUPERVISOR_EVALUATION
    }

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Student getEvaluatedStudent() { return evaluatedStudent; }
    public void setEvaluatedStudent(Student evaluatedStudent) { this.evaluatedStudent = evaluatedStudent; }

    public Long getEvaluatorId() { return evaluatorId; }
    public void setEvaluatorId(Long evaluatorId) { this.evaluatorId = evaluatorId; }

    public EvaluatorType getEvaluatorType() { return evaluatorType; }
    public void setEvaluatorType(EvaluatorType evaluatorType) { this.evaluatorType = evaluatorType; }

    public String getEvaluatorName() { return evaluatorName; }
    public void setEvaluatorName(String evaluatorName) { this.evaluatorName = evaluatorName; }

    public Assessment getAssessment() { return assessment; }
    public void setAssessment(Assessment assessment) { this.assessment = assessment; }

    public String getCommentText() { 
        return commentText != null ? org.springframework.web.util.HtmlUtils.htmlUnescape(commentText) : null; 
    }
    public void setCommentText(String commentText) { this.commentText = commentText; }

    public CommentAssessmentType getAssessmentType() { return assessmentType; }
    public void setAssessmentType(CommentAssessmentType assessmentType) { this.assessmentType = assessmentType; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public String getAnonymousIdentifier() { return anonymousIdentifier; }
    public void setAnonymousIdentifier(String anonymousIdentifier) { this.anonymousIdentifier = anonymousIdentifier; }
    
    public Integer getCommentIndex() { return commentIndex != null ? commentIndex : 0; }
    public void setCommentIndex(Integer commentIndex) { this.commentIndex = commentIndex; }
    
    public String getCommentLabel() { return commentLabel; }
    public void setCommentLabel(String commentLabel) { this.commentLabel = commentLabel; }
    
    public String getRubricAssessmentType() { return rubricAssessmentType; }
    public void setRubricAssessmentType(String rubricAssessmentType) { this.rubricAssessmentType = rubricAssessmentType; }
    
    public Long getRubricId() { return rubricId; }
    public void setRubricId(Long rubricId) { this.rubricId = rubricId; }

    public String getDisplayName() {
        if (cachedDisplayName != null) {
            return cachedDisplayName;
        }
        
        if (evaluatorType == EvaluatorType.STUDENT) {
            if (assessment != null) {
                Boolean isAnonymous = assessment.getCommentsAnonymousForType(rubricAssessmentType);
                if (isAnonymous != null && !isAnonymous) {
                    return evaluatorName != null ? evaluatorName : "Student";
                }
            }
            return anonymousIdentifier != null ? anonymousIdentifier : "Teammate";
        } else if (evaluatorType == EvaluatorType.LECTURER || evaluatorType == EvaluatorType.SUPERVISOR) {

            if (anonymousIdentifier != null && !anonymousIdentifier.equals(evaluatorName)) {
                return anonymousIdentifier; 
            }
            return evaluatorName != null ? evaluatorName : "Evaluator";
        } else {
            return evaluatorName != null ? evaluatorName : "Evaluator";
        }
    }

    public void setDisplayName(String displayName) {
        this.cachedDisplayName = displayName;
    }
    
    public String getDisplayLabel(Assessment liveAssessment, Rubric liveRubric) {
        int idx = (commentIndex != null) ? commentIndex : 0;
        Assessment targetAssessment = (liveAssessment != null) ? liveAssessment : this.assessment;

        if (rubricId != null) {
            Rubric targetRubric = liveRubric;
            if (targetRubric == null && targetAssessment != null && targetAssessment.getRubrics() != null) {
                for (Rubric r : targetAssessment.getRubrics()) {
                    if (r.getId() != null && r.getId().equals(rubricId)) {
                        targetRubric = r;
                        break;
                    }
                }
            }
            if (targetRubric != null) {
                String liveLabel = targetRubric.getRubricCommentLabel(idx);
                if (liveLabel != null && !liveLabel.trim().isEmpty() && !liveLabel.startsWith("Rubric Comment ")) {
                    return liveLabel;
                }
            }
        }

        if (rubricId == null && targetAssessment != null) {
            boolean isGroup = (rubricAssessmentType != null && rubricAssessmentType.toLowerCase().contains("group"))
                           || assessmentType == CommentAssessmentType.TEAM;
            if (isGroup) {
                String liveLabel = targetAssessment.getGroupCommentLabel(idx);
                if (liveLabel != null && !liveLabel.trim().isEmpty() && !liveLabel.startsWith("Group Comment ")) {
                    return liveLabel;
                }
            } else {
                String liveLabel = targetAssessment.getIndividualCommentLabel(idx);
                if (liveLabel != null && !liveLabel.trim().isEmpty() && !liveLabel.startsWith("Individual Comment ")) {
                    return liveLabel;
                }
            }
        }

        if (commentLabel != null && !commentLabel.trim().isEmpty() 
            && !commentLabel.startsWith("Individual Comment ")
            && !commentLabel.startsWith("Group Comment ")
            && !commentLabel.startsWith("Rubric Comment ")) {
            return commentLabel;
        }

        if (commentLabel != null && !commentLabel.trim().isEmpty()) {
            return commentLabel;
        }

        if (rubricId != null) {
            return "Rubric Comment " + (idx + 1);
        } else if (rubricAssessmentType != null && rubricAssessmentType.toLowerCase().contains("group")) {
            return "Group Comment " + (idx + 1);
        } else {
            return "Individual Comment " + (idx + 1);
        }
    }

    public String getDisplayLabel(Assessment liveAssessment) {
        return getDisplayLabel(liveAssessment, null);
    }

    public String getDisplayLabel() {
        return getDisplayLabel(this.assessment, null);
    }
}