package com.capstone.adproject.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "rating")
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 10000)
    @Size(max = 10000, message = "Rating name must be at most 10000 characters")
    private String name; 

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    private BigDecimal marks; 

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_rubric_id")
    private SubRubric subRubric;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rubric_id")
    private Rubric rubric;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getMarks() { return marks; }
    public void setMarks(BigDecimal marks) { this.marks = marks; }

    public SubRubric getSubRubric() { return subRubric; }
    public void setSubRubric(SubRubric subRubric) { this.subRubric = subRubric; }

    public Rubric getRubric() { return rubric; }
    public void setRubric(Rubric rubric) { this.rubric = rubric; }

    public boolean belongsToSubRubric() {
        return subRubric != null;
    }

    public boolean belongsToRubric() {
        return rubric != null && subRubric == null;
    }
}