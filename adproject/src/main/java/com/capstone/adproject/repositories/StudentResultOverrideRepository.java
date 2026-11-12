package com.capstone.adproject.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.StudentResultOverride;

public interface StudentResultOverrideRepository extends JpaRepository<StudentResultOverride, Long> {
    Optional<StudentResultOverride> findByStudent(Student student);

    Optional<StudentResultOverride> findByStudentAndCourseId(Student student, Long courseId);

    Optional<StudentResultOverride> findByStudentIdAndCourseId(Long studentId, Long courseId);

    List<StudentResultOverride> findByStudentIn(List<Student> students);

    List<StudentResultOverride> findByStudentInAndCourseId(List<Student> students, Long courseId);

    List<StudentResultOverride> findByCourseId(Long courseId);

    @Modifying
    @Transactional
    void deleteByStudentIdAndCourseId(Long studentId, Long courseId);

    @Modifying
    @Transactional
    void deleteByCourseId(Long courseId);
}