package com.capstone.adproject.model;

import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "student_result_overrides", uniqueConstraints = {
    @UniqueConstraint(name = "uk_override_student_course", columnNames = {"student_id", "course_id"})
})
public class StudentResultOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id", foreignKey = @ForeignKey(name = "fk_student_result_overrides_student"))
    private Student student;

    @ManyToOne
    @JoinColumn(name = "course_id", foreignKey = @ForeignKey(name = "fk_student_result_overrides_course"))
    private Course course;

    private Double overriddenFactor;

    private Double overriddenGrandTotal;

    public StudentResultOverride() {}

    public StudentResultOverride(Student student, Double overriddenFactor, Double overriddenGrandTotal) {
        this(student, student != null ? student.getCourse() : null, overriddenFactor, overriddenGrandTotal);
    }

    public StudentResultOverride(Student student, Course course, Double overriddenFactor, Double overriddenGrandTotal) {
        this.student = student;
        this.course = course;
        this.overriddenFactor = overriddenFactor;
        this.overriddenGrandTotal = overriddenGrandTotal;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    public Double getOverriddenFactor() { return overriddenFactor; }
    public void setOverriddenFactor(Double overriddenFactor) { this.overriddenFactor = overriddenFactor; }
    public Double getOverriddenGrandTotal() { return overriddenGrandTotal; }
    public void setOverriddenGrandTotal(Double overriddenGrandTotal) { this.overriddenGrandTotal = overriddenGrandTotal; }
}