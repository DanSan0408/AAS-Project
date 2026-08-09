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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "marks")
public class Mark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluated_student_id", nullable = false, foreignKey = @ForeignKey(name = "fk_marks_evaluated_student"))
    private Student evaluatedStudent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluator_student_id", nullable = false, foreignKey = @ForeignKey(name = "fk_marks_evaluator_student"))
    private Student evaluatorStudent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    private Assessment assessment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rubric_id")
    private Rubric rubric;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_rubric_id")
    private SubRubric subRubric;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rating_id")
    private Rating rating;

    @Column(nullable = false)
    private Double markValue;

    @Column(nullable = false)
    private Integer clo;

    @Column(nullable = false)
    private Double cloMarks;

    @Column(name = "assessment_type", nullable = false)
    private String assessmentType;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    @Column(columnDefinition = "TEXT")
    private String comments;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubmissionStatus status = SubmissionStatus.DRAFT;

    @Column(name = "supervisor_id")
    private Long supervisorId;

    @Column(name = "is_supervisor_mark")
    private Boolean isSupervisorMark = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lecturer_id")
    private Lecturer lecturer;

    public enum SubmissionStatus {
        DRAFT,
        SUBMITTED,
        FINAL
    }

    public Mark() {
        this.status = SubmissionStatus.DRAFT;
        this.submittedAt = LocalDateTime.now();
    }

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

    public Long getSupervisorId() {
        return supervisorId;
    }

    public void setSupervisorId(Long supervisorId) {
        this.supervisorId = supervisorId;
    }

    public Boolean getIsSupervisorMark() {
        return isSupervisorMark;
    }

    public void setIsSupervisorMark(Boolean isSupervisorMark) {
        this.isSupervisorMark = isSupervisorMark;
    }

    public Lecturer getLecturer() {
        return lecturer;
    }

    public void setLecturer(Lecturer lecturer) {
        this.lecturer = lecturer;
    }

    public void calculateMarkValue() {
        if (rating != null && rating.getMarks() != null) {
            this.markValue = rating.getMarks().doubleValue();
        }
    }

    public void calculateCloMarks() {
        if (rubric != null && rubric.getCloMarks() != null && rating != null && rating.getMarks() != null) {
            
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
        
        if (this.clo == null && rubric != null && rubric.getClo() != null) {
            this.clo = rubric.getClo();
        }
        if (this.clo == null) {
            this.clo = 0;
        }
        
        if (this.assessmentType == null && rubric != null && rubric.getAssessmentTypes() != null) {
            this.assessmentType = rubric.getAssessmentTypes();
        }
        if (this.assessmentType == null) {
            this.assessmentType = "Peer Assessment";
        }
        
        if (this.markValue == null) {
            this.markValue = 0.0;
        }
        if (this.cloMarks == null) {
            this.cloMarks = 0.0;
        }
    }
}