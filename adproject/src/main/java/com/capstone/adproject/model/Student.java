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
@Table(name = "student")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    // --- New Code for Group Assignment ---

    // Establishes a Many-to-One relationship with the Group entity.
    // Many students can belong to one group.
    @ManyToOne
    @JoinColumn(name = "group_id") // This specifies the foreign key column name in the 'student' table
    private Group group; 
    
    // Note: Lombok's @Data annotation automatically generates the getter (getGroup) 
    // and setter (setGroup) for this new field.

    @Column(unique = true, nullable = false) // Added unique constraint for email
    private String email;
    
    private String resetPasswordToken;
}