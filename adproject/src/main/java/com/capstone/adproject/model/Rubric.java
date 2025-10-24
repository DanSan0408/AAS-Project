package com.capstone.adproject.model;

import java.math.BigDecimal;
import java.util.ArrayList; // <-- Import ArrayList
import java.util.List; // <-- Import List

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "rubric") // Added table name for completeness
public class Rubric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;
    private BigDecimal marks;
    private Integer clo;
    private Double cloMarks;

    private String evaluationType;
    private String assessmentTypes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id")
    private Assessment assessment;

    @OneToMany(mappedBy = "rubric", cascade = CascadeType.ALL, orphanRemoval = true)
    // FIX: Changed Set to List to allow indexed data binding
    private List<SubRubric> subRubrics = new ArrayList<>();

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getMarks() { return marks; }
    public void setMarks(BigDecimal marks) { this.marks = marks; }
    public Assessment getAssessment() { return assessment; }
    public void setAssessment(Assessment assessment) { this.assessment = assessment; }
    
    // Updated Getters/Setters for List
    public List<SubRubric> getSubRubrics() { return subRubrics; }
    public void setSubRubrics(List<SubRubric> subRubrics) { this.subRubrics = subRubrics; }

    public void addSubRubric(SubRubric subRubric) {
        if (subRubric != null) {
            if (this.subRubrics == null) {
                this.subRubrics = new ArrayList<>(); // Use ArrayList
            }
            subRubric.setRubric(this); 
            this.subRubrics.add(subRubric); 
        }
    }
    
    public Integer getClo() { return clo; }
    public void setClo(Integer clo) { this.clo = clo; }
    public Double getCloMarks() { return cloMarks; }
    public void setCloMarks(Double cloMarks) { this.cloMarks = cloMarks; }
    public String getEvaluationType() { return evaluationType; }
    public void setEvaluationType(String evaluationType) { this.evaluationType = evaluationType; }
    public String getAssessmentTypes() { return assessmentTypes; }
    public void setAssessmentTypes(String assessmentTypes) { this.assessmentTypes = assessmentTypes; }
}