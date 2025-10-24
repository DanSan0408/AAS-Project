package com.capstone.adproject.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class CriteriaRating {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ADDED: Explicit level field (0, 1, 2, 3, 4) to mirror the Rating model
    private Integer level;

    private String name;
    private String description;
    // ratingMark will store the numerical value of the level (0.0, 1.0, 2.0, etc.)
    private Double ratingMark; 

    @ManyToOne
    @JoinColumn(name = "criteria_id")
    private Criteria criteria;

    // Default constructor
    public CriteriaRating() {
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    // ADDED Getter and Setter for level
    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getRatingMark() {
        return ratingMark;
    }

    public void setRatingMark(Double ratingMark) {
        this.ratingMark = ratingMark;
    }

    public Criteria getCriteria() {
        return criteria;
    }

    public void setCriteria(Criteria criteria) {
        this.criteria = criteria;
    }
}