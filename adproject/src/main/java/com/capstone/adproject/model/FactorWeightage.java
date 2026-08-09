package com.capstone.adproject.model;

import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "factor_weightages")
public class FactorWeightage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "course_id", nullable = false, foreignKey = @ForeignKey(name = "fk_factor_weightage_course"))
    private Course course;

    @ManyToOne
    @JoinColumn(name = "assessment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_factor_weightage_assessment"))
    private Assessment assessment;

    private Double weightage;

    @Column(name = "factor_contribution_type")
    private String factorContributionType = "BOTH";

    public FactorWeightage() {}

    public FactorWeightage(Course course, Assessment assessment, Double weightage) {
        this.course = course;
        this.assessment = assessment;
        this.weightage = weightage;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }

    public Assessment getAssessment() { return assessment; }
    public void setAssessment(Assessment assessment) { this.assessment = assessment; }

    public Double getWeightage() { return weightage; }
    public void setWeightage(Double weightage) { this.weightage = weightage; }
    
    public String getFactorContributionType() { return factorContributionType; }
    public void setFactorContributionType(String factorContributionType) { this.factorContributionType = factorContributionType; }
}
