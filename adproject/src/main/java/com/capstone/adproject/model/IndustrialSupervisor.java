package com.capstone.adproject.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "industrial_supervisor")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IndustrialSupervisor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ CHANGED: Username is now NULLABLE (kept for backward compatibility)
    @Column(unique = true, nullable = true)
    private String username;

    @Column(nullable = false)
    private String password;

    // ✅ CHANGED: Email is now the primary identifier for login
    @Column(unique = true, nullable = false)
    private String email;
    
    private String resetPasswordToken;

    // ✅ NEW: Flag to track if password is temporary
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isTempPassword = false;
}