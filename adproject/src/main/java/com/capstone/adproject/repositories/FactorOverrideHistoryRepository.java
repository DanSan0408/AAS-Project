package com.capstone.adproject.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.model.FactorOverrideHistory;
import com.capstone.adproject.model.Student;

public interface FactorOverrideHistoryRepository extends JpaRepository<FactorOverrideHistory, Long> {
    List<FactorOverrideHistory> findByStudentOrderByChangedAtDesc(Student student);
    List<FactorOverrideHistory> findByStudentInOrderByChangedAtDesc(List<Student> students);
    List<FactorOverrideHistory> findByStudentAndCourseIdOrderByChangedAtDesc(Student student, Long courseId);
    List<FactorOverrideHistory> findByStudentInAndCourseIdOrderByChangedAtDesc(List<Student> students, Long courseId);

    @Modifying
    @Transactional
    void deleteByStudentIdAndCourseId(Long studentId, Long courseId);

    @Modifying
    @Transactional
    void deleteByCourseId(Long courseId);
}
