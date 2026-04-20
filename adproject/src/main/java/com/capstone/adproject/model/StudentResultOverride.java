package com.capstone.adproject.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "student_result_overrides")
public class StudentResultOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "student_id", unique = true)
    private Student student;

    private Double overriddenFactor;

    private Double overriddenGrandTotal;

    public StudentResultOverride() {}

    public StudentResultOverride(Student student, Double overriddenFactor, Double overriddenGrandTotal) {
        this.student = student;
        this.overriddenFactor = overriddenFactor;
        this.overriddenGrandTotal = overriddenGrandTotal;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }
    public Double getOverriddenFactor() { return overriddenFactor; }
    public void setOverriddenFactor(Double overriddenFactor) { this.overriddenFactor = overriddenFactor; }
    public Double getOverriddenGrandTotal() { return overriddenGrandTotal; }
    public void setOverriddenGrandTotal(Double overriddenGrandTotal) { this.overriddenGrandTotal = overriddenGrandTotal; }
}