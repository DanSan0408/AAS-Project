package com.capstone.adproject.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
@Table(name = "rubric")
public class Rubric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;
    private BigDecimal marks; // Total marks for this rubric
    private Integer clo;
    private Double cloMarks;

    // assessmentTypes kept for categorization (Individual Assessment, Group Assessment)
    private String assessmentTypes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id")
    private Assessment assessment;

    @OneToMany(mappedBy = "rubric", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SubRubric> subRubrics = new ArrayList<>();

    // Direct ratings on the rubric (when no sub-rubrics are used)
    @OneToMany(mappedBy = "rubric", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Rating> ratings = new ArrayList<>();

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
    
    public List<SubRubric> getSubRubrics() { 
        if (subRubrics == null) {
            subRubrics = new ArrayList<>();
        }
        return subRubrics; 
    }
    
    public void setSubRubrics(List<SubRubric> subRubrics) { 
        this.subRubrics = subRubrics; 
    }

    public List<Rating> getRatings() {
        if (ratings == null) {
            ratings = new ArrayList<>();
        }
        return ratings;
    }

    public void setRatings(List<Rating> ratings) {
        this.ratings = ratings;
    }

    public void addSubRubric(SubRubric subRubric) {
        if (subRubric != null) {
            if (this.subRubrics == null) {
                this.subRubrics = new ArrayList<>();
            }
            subRubric.setRubric(this); 
            this.subRubrics.add(subRubric); 
        }
    }

    public void addRating(Rating rating) {
        if (rating != null) {
            if (this.ratings == null) {
                this.ratings = new ArrayList<>();
            }
            rating.setRubric(this);
            this.ratings.add(rating);
        }
    }
    
    public Integer getClo() { return clo; }
    public void setClo(Integer clo) { this.clo = clo; }
    
    public Double getCloMarks() { return cloMarks; }
    public void setCloMarks(Double cloMarks) { this.cloMarks = cloMarks; }
    
    public String getAssessmentTypes() { return assessmentTypes; }
    public void setAssessmentTypes(String assessmentTypes) { this.assessmentTypes = assessmentTypes; }

    // Helper method to check if this rubric has sub-rubrics
    public boolean hasSubRubrics() {
        return subRubrics != null && !subRubrics.isEmpty();
    }

    // Helper method to check if this rubric has direct ratings
    public boolean hasDirectRatings() {
        return ratings != null && !ratings.isEmpty();
    }

    // Calculate total marks from sub-rubrics and direct ratings
    public BigDecimal calculateChildrenMarks() {
        BigDecimal total = BigDecimal.ZERO;
        
        if (subRubrics != null) {
            for (SubRubric sr : subRubrics) {
                if (sr.getMarks() != null) {
                    total = total.add(sr.getMarks());
                }
            }
        }
        
        if (ratings != null) {
            for (Rating r : ratings) {
                if (r.getMarks() != null) {
                    total = total.add(r.getMarks());
                }
            }
        }
        
        return total;
    }
}