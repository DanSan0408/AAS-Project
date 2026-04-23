package com.capstone.adproject.model;

import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "student_course_assignment", indexes = {
    @Index(name = "idx_sca_course", columnList = "course_id"),
    @Index(name = "idx_sca_student", columnList = "student_id")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_sca_student_course", columnNames = {"student_id", "course_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentCourseAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sca_student"))
    private Student student;

    @ManyToOne(optional = false)
    @JoinColumn(name = "course_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sca_course"))
    private Course course;
}
