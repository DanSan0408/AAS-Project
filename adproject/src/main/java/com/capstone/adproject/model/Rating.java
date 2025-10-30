package com.capstone.adproject.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "rating")
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer level;
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_rubric_id", nullable = false)
    private SubRubric subRubric;

    // === Getters and Setters ===
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public SubRubric getSubRubric() { return subRubric; }
    public void setSubRubric(SubRubric subRubric) { this.subRubric = subRubric; }

    public String getLevelLabel() {
        if (level == null) return "";
        switch (level) {
            case 0: return "0 - Unsatisfactory";
            case 1: return "1 - Needs Improvement";
            case 2: return "2 - Satisfactory";
            case 3: return "3 - Good";
            case 4: return "4 - Excellent";
            default: return "";
        }
    }
}
