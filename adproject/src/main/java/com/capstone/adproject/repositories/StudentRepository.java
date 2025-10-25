package com.capstone.adproject.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.capstone.adproject.model.Student;

public interface StudentRepository extends JpaRepository<Student, Long> {
    
    Optional<Student> findByUsername(String username);

    // New method to find students not assigned to a group (group is NULL)
    List<Student> findByGroupIsNull();
}