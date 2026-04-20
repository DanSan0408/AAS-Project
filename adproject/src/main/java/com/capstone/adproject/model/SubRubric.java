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
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "sub_rubric")
public class SubRubric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.Column(name = "name", length = 10000)
    @Size(max = 10000, message = "Sub-rubric name must be at most 10000 characters")
    private String name;
    private String description;
    private BigDecimal marks; 

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rubric_id")
    private Rubric rubric;

    @OneToMany(mappedBy = "subRubric", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Rating> ratings = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getMarks() { return marks; }
    public void setMarks(BigDecimal marks) { this.marks = marks; }

    public Rubric getRubric() { return rubric; }
    public void setRubric(Rubric rubric) { this.rubric = rubric; }

    public List<Rating> getRatings() {
        if (ratings == null) {
            ratings = new ArrayList<>();
        }
        return ratings;
    }

    public void setRatings(List<Rating> ratings) {
        this.getRatings().clear();
        if (ratings != null) {
            for (Rating r : ratings) {
                addRating(r);
            }
        }
    }

    public void addRating(Rating rating) {
        if (rating != null) {
            rating.setSubRubric(this);
            rating.setRubric(null); 
            getRatings().add(rating);
        }
    }

    public void removeRating(Rating rating) {
        if (rating != null) {
            getRatings().remove(rating);
            rating.setSubRubric(null);
        }
    }

    public BigDecimal calculateRatingsMarks() {
        BigDecimal total = BigDecimal.ZERO;
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