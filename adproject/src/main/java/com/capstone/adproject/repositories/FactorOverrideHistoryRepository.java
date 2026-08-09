package com.capstone.adproject.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.capstone.adproject.model.FactorOverrideHistory;
import com.capstone.adproject.model.Student;

public interface FactorOverrideHistoryRepository extends JpaRepository<FactorOverrideHistory, Long> {
    List<FactorOverrideHistory> findByStudentOrderByChangedAtDesc(Student student);
    List<FactorOverrideHistory> findByStudentInOrderByChangedAtDesc(List<Student> students);
}
