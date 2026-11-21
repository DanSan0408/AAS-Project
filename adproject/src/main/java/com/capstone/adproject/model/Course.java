package com.capstone.adproject.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "courses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "courseName")
    private String courseName;
    
    @NotBlank
    @Column(name = "courseCode")
    private String courseCode;
    
    private String description;

    /** The mark a student needs to pass the course. Null means the default (50). */
    @Column(name = "pass_mark")
    private Double passMark;

    /** The share of a learning outcome's marks the class should reach. Null means the default (50%). */
    @Column(name = "clo_target_percent")
    private Double cloTargetPercent;

    public static final double DEFAULT_PASS_MARK = 50.0;
    public static final double DEFAULT_CLO_TARGET_PERCENT = 50.0;

    public double effectivePassMark() {
        return passMark != null ? passMark : DEFAULT_PASS_MARK;
    }

    public double effectiveCloTargetPercent() {
        return cloTargetPercent != null ? cloTargetPercent : DEFAULT_CLO_TARGET_PERCENT;
    }

    @ManyToOne
    @JoinColumn(name = "created_by_lecturer_id", foreignKey = @ForeignKey(name = "fk_course_created_by"))
    private Lecturer createdBy;
}