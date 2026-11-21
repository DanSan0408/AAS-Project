package com.capstone.adproject.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Audit record written whenever an admin deletes a finished evaluation. It keeps who, when, why and a readable copy of
 * what was deleted, so a deletion can be explained (or the marks re-entered by hand) afterwards.
 */
@Entity
@Table(name = "evaluation_deletions", indexes = @Index(name = "idx_evaluation_deletions_assessment", columnList = "assessment_id"))
public class EvaluationDeletionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assessment_id", nullable = false)
    private Long assessmentId;

    @Column(name = "assessment_title", length = 255)
    private String assessmentTitle;

    /** LECTURER or STUDENT. */
    @Column(name = "evaluator_kind", nullable = false, length = 20)
    private String evaluatorKind;

    @Column(name = "evaluator_id")
    private Long evaluatorId;

    @Column(name = "evaluator_name", length = 255)
    private String evaluatorName;

    @Column(name = "evaluated_names", length = 1000)
    private String evaluatedNames;

    @Column(name = "rubric_name", length = 255)
    private String rubricName;

    @Column(name = "assessment_type", length = 60)
    private String assessmentType;

    @Column(name = "mark_count", nullable = false)
    private int markCount;

    @Column(name = "total_marks")
    private Double totalMarks;

    /** Plain-text copy of the deleted ratings and comments. */
    @Column(columnDefinition = "TEXT")
    private String snapshot;

    @Column(nullable = false, length = 300)
    private String reason;

    @Column(name = "deleted_by", nullable = false, length = 100)
    private String deletedBy;

    @Column(name = "deleted_at", nullable = false)
    private LocalDateTime deletedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getAssessmentId() { return assessmentId; }
    public void setAssessmentId(Long assessmentId) { this.assessmentId = assessmentId; }
    public String getAssessmentTitle() { return assessmentTitle; }
    public void setAssessmentTitle(String assessmentTitle) { this.assessmentTitle = assessmentTitle; }
    public String getEvaluatorKind() { return evaluatorKind; }
    public void setEvaluatorKind(String evaluatorKind) { this.evaluatorKind = evaluatorKind; }
    public Long getEvaluatorId() { return evaluatorId; }
    public void setEvaluatorId(Long evaluatorId) { this.evaluatorId = evaluatorId; }
    public String getEvaluatorName() { return evaluatorName; }
    public void setEvaluatorName(String evaluatorName) { this.evaluatorName = evaluatorName; }
    public String getEvaluatedNames() { return evaluatedNames; }
    public void setEvaluatedNames(String evaluatedNames) { this.evaluatedNames = evaluatedNames; }
    public String getRubricName() { return rubricName; }
    public void setRubricName(String rubricName) { this.rubricName = rubricName; }
    public String getAssessmentType() { return assessmentType; }
    public void setAssessmentType(String assessmentType) { this.assessmentType = assessmentType; }
    public int getMarkCount() { return markCount; }
    public void setMarkCount(int markCount) { this.markCount = markCount; }
    public Double getTotalMarks() { return totalMarks; }
    public void setTotalMarks(Double totalMarks) { this.totalMarks = totalMarks; }
    public String getSnapshot() { return snapshot; }
    public void setSnapshot(String snapshot) { this.snapshot = snapshot; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getDeletedBy() { return deletedBy; }
    public void setDeletedBy(String deletedBy) { this.deletedBy = deletedBy; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
