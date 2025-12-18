package com.capstone.adproject.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "calculated_results")
public class CalculatedResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    private Assessment assessment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rubric_id")
    private Rubric rubric;

    // Student factor from peer/self assessment
    @Column(name = "student_factor")
    private Double studentFactor;

    // Rubric-specific calculations
    @Column(name = "rubric_factor")
    private Double rubricFactor; // Rf

    @Column(name = "configured_rubric_mark")
    private Double configuredRubricMark; // Rm

    @Column(name = "evaluated_rubric_mark")
    private Double evaluatedRubricMark; // Mr = Rf × Rm

    @Column(name = "weighted_rubric_mark")
    private Double weightedRubricMark; // Wr = f × Mr (for group only)

    @Column(name = "assessment_type")
    private String assessmentType; // "Group Assessment" or "Individual Assessment"

    @Column(name = "clo")
    private Integer clo;

    // Aggregate totals (stored per student per assessment)
    @Column(name = "total_group_marks")
    private Double totalGroupMarks; // Tg

    @Column(name = "total_individual_marks")
    private Double totalIndividualMarks; // Ts

    @Column(name = "clo_total_marks", columnDefinition = "TEXT")
    private String cloTotalMarksJson; // SClonMrm (JSON format)

    @Column(name = "clo_weighted_marks", columnDefinition = "TEXT")
    private String cloWeightedMarksJson; // SClonWrm (JSON format)

    @Column(name = "final_total_marks")
    private Double finalTotalMarks; // T

    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt;

    @Column(name = "calculation_type")
    private String calculationType; // "SUB_RUBRIC" or "DIRECT_RATING"

    // Constructors
    public CalculatedResult() {
        this.calculatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public Assessment getAssessment() { return assessment; }
    public void setAssessment(Assessment assessment) { this.assessment = assessment; }

    public Rubric getRubric() { return rubric; }
    public void setRubric(Rubric rubric) { this.rubric = rubric; }

    public Double getStudentFactor() { return studentFactor; }
    public void setStudentFactor(Double studentFactor) { this.studentFactor = studentFactor; }

    public Double getRubricFactor() { return rubricFactor; }
    public void setRubricFactor(Double rubricFactor) { this.rubricFactor = rubricFactor; }

    public Double getConfiguredRubricMark() { return configuredRubricMark; }
    public void setConfiguredRubricMark(Double configuredRubricMark) { 
        this.configuredRubricMark = configuredRubricMark; 
    }

    public Double getEvaluatedRubricMark() { return evaluatedRubricMark; }
    public void setEvaluatedRubricMark(Double evaluatedRubricMark) { 
        this.evaluatedRubricMark = evaluatedRubricMark; 
    }

    public Double getWeightedRubricMark() { return weightedRubricMark; }
    public void setWeightedRubricMark(Double weightedRubricMark) { 
        this.weightedRubricMark = weightedRubricMark; 
    }

    public String getAssessmentType() { return assessmentType; }
    public void setAssessmentType(String assessmentType) { this.assessmentType = assessmentType; }

    public Integer getClo() { return clo; }
    public void setClo(Integer clo) { this.clo = clo; }

    public Double getTotalGroupMarks() { return totalGroupMarks; }
    public void setTotalGroupMarks(Double totalGroupMarks) { this.totalGroupMarks = totalGroupMarks; }

    public Double getTotalIndividualMarks() { return totalIndividualMarks; }
    public void setTotalIndividualMarks(Double totalIndividualMarks) { 
        this.totalIndividualMarks = totalIndividualMarks; 
    }

    public String getCloTotalMarksJson() { return cloTotalMarksJson; }
    public void setCloTotalMarksJson(String cloTotalMarksJson) { 
        this.cloTotalMarksJson = cloTotalMarksJson; 
    }

    public String getCloWeightedMarksJson() { return cloWeightedMarksJson; }
    public void setCloWeightedMarksJson(String cloWeightedMarksJson) { 
        this.cloWeightedMarksJson = cloWeightedMarksJson; 
    }

    public Double getFinalTotalMarks() { return finalTotalMarks; }
    public void setFinalTotalMarks(Double finalTotalMarks) { this.finalTotalMarks = finalTotalMarks; }

    public LocalDateTime getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(LocalDateTime calculatedAt) { this.calculatedAt = calculatedAt; }

    public String getCalculationType() { return calculationType; }
    public void setCalculationType(String calculationType) { this.calculationType = calculationType; }

    @PrePersist
    @PreUpdate
    public void prePersistOrUpdate() {
        if (calculatedAt == null) {
            calculatedAt = LocalDateTime.now();
        }
    }
}