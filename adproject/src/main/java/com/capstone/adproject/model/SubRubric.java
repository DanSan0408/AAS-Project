package com.capstone.adproject.model;

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
@Table(name = "sub_rubric")
public class SubRubric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rubric_id")
    private Rubric rubric;

    @OneToMany(mappedBy = "subRubric", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Rating> ratings = new ArrayList<>();

    // === Getters and Setters ===
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Rubric getRubric() { return rubric; }
    public void setRubric(Rubric rubric) { this.rubric = rubric; }

    public List<Rating> getRatings() {
        if (ratings == null) {
            ratings = new ArrayList<>();
        }
        return ratings;
    }

    /**
     * Prevent replacing the managed collection entirely — Hibernate-safe.
     */
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
            getRatings().add(rating);
        }
    }

    public void removeRating(Rating rating) {
        if (rating != null) {
            getRatings().remove(rating);
            rating.setSubRubric(null);
        }
    }
}
