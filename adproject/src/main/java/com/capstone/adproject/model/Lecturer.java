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
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Filter(name = "courseScopeFilter", condition = "course_id = :activeCourseId")
@Entity
@Table(name = "lecturer", indexes = {
    @Index(name = "idx_lecturer_course", columnList = "course_id"),
    @Index(name = "idx_lecturer_email", columnList = "email", unique = true),
    @Index(name = "idx_lecturer_username", columnList = "username", unique = true)
})

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Lecturer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(unique = true, nullable = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @NotBlank
    @Email
    @Column(unique = true, nullable = false)
    private String email;
    
    private String resetPasswordToken;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isTempPassword = false;

    private String roles; // Added for multi-role support
    @ManyToOne
    @JoinColumn(name = "course_id")
    private Course course; // Added for multi-course support
}