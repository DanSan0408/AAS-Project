package com.capstone.adproject.model;

import org.hibernate.annotations.Filter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Filter(name = "courseScopeFilter", condition = "(course_id = :activeCourseId OR EXISTS (SELECT 1 FROM student_course_assignment sca WHERE sca.student_id = id AND sca.course_id = :activeCourseId) OR EXISTS (SELECT 1 FROM project_group pg WHERE pg.id = group_id AND pg.course_id = :activeCourseId))")
@Entity
@Table(name = "student", indexes = {
    @Index(name = "idx_student_course", columnList = "course_id"),
    @Index(name = "idx_student_group", columnList = "group_id"),
    @Index(name = "idx_student_email_course", columnList = "email,course_id", unique = true),
    @Index(name = "idx_student_username", columnList = "username", unique = true)
}, uniqueConstraints = @UniqueConstraint(name = "uk_student_email_course", columnNames = {"email", "course_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(unique = true, nullable = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @ManyToOne
    @JoinColumn(name = "group_id")
    private Group group; 

    @NotBlank
    @Email
    @Column(nullable = false)
    private String email;
    
    private String resetPasswordToken;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isTempPassword = false;

    @ManyToOne
    @JoinColumn(name = "course_id")
    private Course course; // Added for multi-course support
}