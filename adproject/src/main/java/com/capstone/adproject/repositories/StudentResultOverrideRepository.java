package com.capstone.adproject.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.StudentResultOverride;

public interface StudentResultOverrideRepository extends JpaRepository<StudentResultOverride, Long> {
    Optional<StudentResultOverride> findByStudent(Student student);
}