package com.capstone.adproject.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.capstone.adproject.model.IndustrialSupervisor;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.repositories.IndustrialSupervisorRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.StudentRepository;

@Service
public class AdminService {

    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;
    private final IndustrialSupervisorRepository industrialSupervisorRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public AdminService(StudentRepository studentRepository, LecturerRepository lecturerRepository, IndustrialSupervisorRepository industrialSupervisorRepository, PasswordEncoder passwordEncoder) {
        this.studentRepository = studentRepository;
        this.lecturerRepository = lecturerRepository;
        this.industrialSupervisorRepository = industrialSupervisorRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Student> getAllStudents() {
        return studentRepository.findAll();
    }

    public List<Lecturer> getAllLecturers() {
        return lecturerRepository.findAll();
    }

    public List<IndustrialSupervisor> getAllIndustrialSupervisors() {
        return industrialSupervisorRepository.findAll();
    }

    public Optional<Student> findStudentById(Long id) {
        return studentRepository.findById(id);
    }

    public Optional<Lecturer> findLecturerById(Long id) {
        return lecturerRepository.findById(id);
    }

    public Optional<IndustrialSupervisor> findIndustrialSupervisorById(Long id) {
        return industrialSupervisorRepository.findById(id);
    }

    public void saveStudent(Student student) {
        if (student.getPassword() != null && !student.getPassword().isEmpty()) {
            student.setPassword(passwordEncoder.encode(student.getPassword()));
        } else {
            // Keep the old password if a new one is not provided during update
            studentRepository.findById(student.getId()).ifPresent(existingStudent -> student.setPassword(existingStudent.getPassword()));
        }
        studentRepository.save(student);
    }

    public void saveLecturer(Lecturer lecturer) {
        if (lecturer.getPassword() != null && !lecturer.getPassword().isEmpty()) {
            lecturer.setPassword(passwordEncoder.encode(lecturer.getPassword()));
        } else {
            lecturerRepository.findById(lecturer.getId()).ifPresent(existingLecturer -> lecturer.setPassword(existingLecturer.getPassword()));
        }
        lecturerRepository.save(lecturer);
    }

    public void saveIndustrialSupervisor(IndustrialSupervisor industrialSupervisor) {
        if (industrialSupervisor.getPassword() != null && !industrialSupervisor.getPassword().isEmpty()) {
            industrialSupervisor.setPassword(passwordEncoder.encode(industrialSupervisor.getPassword()));
        } else {
            industrialSupervisorRepository.findById(industrialSupervisor.getId()).ifPresent(existingSupervisor -> industrialSupervisor.setPassword(existingSupervisor.getPassword()));
        }
        industrialSupervisorRepository.save(industrialSupervisor);
    }

    public void deleteStudentById(Long id) {
        studentRepository.deleteById(id);
    }

    public void deleteLecturerById(Long id) {
        lecturerRepository.deleteById(id);
    }

    public void deleteIndustrialSupervisorById(Long id) {
        industrialSupervisorRepository.deleteById(id);
    }
}
