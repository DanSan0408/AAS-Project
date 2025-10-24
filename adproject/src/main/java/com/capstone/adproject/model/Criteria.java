package com.capstone.adproject.model;

import java.math.BigDecimal;
import java.util.ArrayList; // <-- New import
import java.util.List; // <-- New import

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

@Entity
public class Criteria {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private BigDecimal marks;
    private Integer clo;
    private Double cloMarks;

    @ManyToOne
    @JoinColumn(name = "assessment_id")
    private Assessment assessment;

    private String evaluationType;
    private String assessmentTypes;

    @OneToMany(mappedBy = "criteria", cascade = CascadeType.ALL, orphanRemoval = true)
    // FIX: Changed Set to List
    private List<CriteriaRating> criteriaRatings = new ArrayList<>();

    public Criteria() {
    }

    // FIX: Changed return type and initialization to List
    public List<CriteriaRating> getCriteriaRatings() {
        if (criteriaRatings == null) {
            criteriaRatings = new ArrayList<>();
        }
        return criteriaRatings;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getMarks() { return marks; }
    public void setMarks(BigDecimal marks) { this.marks = marks; }
    public Integer getClo() { return clo; }
    public void setClo(Integer clo) { this.clo = clo; }
    public Double getCloMarks() { return cloMarks; }
    public void setCloMarks(Double cloMarks) { this.cloMarks = cloMarks; }
    public Assessment getAssessment() { return assessment; }
    public void setAssessment(Assessment assessment) { this.assessment = assessment; }
    
    // FIX: Setter now accepts List
    public void setCriteriaRatings(List<CriteriaRating> criteriaRatings) {
        this.criteriaRatings = criteriaRatings;
    }

    public String getEvaluationType() { return evaluationType; }
    public void setEvaluationType(String evaluationType) { this.evaluationType = evaluationType; }
    public String getAssessmentTypes() { return assessmentTypes; }
    public void setAssessmentTypes(String assessmentTypes) { this.assessmentTypes = assessmentTypes; }

    // Optional: Add a helper method to maintain the bi-directional link when adding
    public void addCriteriaRating(CriteriaRating criteriaRating) {
        if (criteriaRating != null) {
            getCriteriaRatings().add(criteriaRating);
            criteriaRating.setCriteria(this);
        }
    }
}