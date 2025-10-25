package com.capstone.adproject.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

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
}