package com.capstone.adproject.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "admin_course_assignment")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminCourseAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "lecturer_id", nullable = false)
    private Lecturer lecturer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    /**
     * True when the person was given this course as an admin; false when they are only an assessor in it. Rows from before
     * this column existed are null and still count as admin, so nobody loses access they already had.
     */
    @Column(name = "as_admin")
    private Boolean asAdmin;

    /** An assessor enrolment (the common case): the person works in the course but does not administer it. */
    public AdminCourseAssignment(Long id, Lecturer lecturer, Course course) {
        this(id, lecturer, course, Boolean.FALSE);
    }

    public boolean grantsAdminOfCourse() {
        return !Boolean.FALSE.equals(asAdmin);
    }
}
