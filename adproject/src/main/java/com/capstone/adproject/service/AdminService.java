package com.capstone.adproject.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.capstone.adproject.dto.GroupAssignmentDto;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.IndustrialSupervisor;
import com.capstone.adproject.model.Lecturer; // Import Group
import com.capstone.adproject.model.Student; // Import DTO
import com.capstone.adproject.repositories.GroupRepository; // Import GroupRepository
import com.capstone.adproject.repositories.IndustrialSupervisorRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.StudentRepository;

@Service
public class AdminService {

    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;
    private final IndustrialSupervisorRepository industrialSupervisorRepository;
    private final GroupRepository groupRepository; // New Repository
    private final PasswordEncoder passwordEncoder;

    // Updated constructor to include GroupRepository
    @Autowired
    public AdminService(StudentRepository studentRepository, LecturerRepository lecturerRepository, IndustrialSupervisorRepository industrialSupervisorRepository, GroupRepository groupRepository, PasswordEncoder passwordEncoder) {
        this.studentRepository = studentRepository;
        this.lecturerRepository = lecturerRepository;
        this.industrialSupervisorRepository = industrialSupervisorRepository;
        this.groupRepository = groupRepository; // Initialize GroupRepository
        this.passwordEncoder = passwordEncoder;
    }
    
    // =========================================================
    // Group Assignment Methods
    // =========================================================

    public List<Student> getStudentsWithoutGroup() {
        return studentRepository.findByGroupIsNull(); 
    }
    
    public List<Group> getAllGroups() {
        return groupRepository.findAll();
    }

    public void assignStudentsToNewGroup(GroupAssignmentDto dto) {
        // 1. Create the new Group entity
        Group newGroup = new Group();
        newGroup.setGroupName(dto.getGroupName());
        
        // 2. Assign Supervisors/Lecturer
        // Find by ID and set on the Group
        lecturerRepository.findById(dto.getAcademicSupervisorId()).ifPresent(newGroup::setAcademicSupervisor);
        industrialSupervisorRepository.findById(dto.getIndustrialSupervisorId()).ifPresent(newGroup::setIndustrialSupervisor);
        
        // Save the group first to generate its ID
        Group savedGroup = groupRepository.save(newGroup);

        // 3. Assign the Group to the selected Students
        if (dto.getSelectedStudentIds() != null && !dto.getSelectedStudentIds().isEmpty()) {
            List<Student> studentsToAssign = studentRepository.findAllById(dto.getSelectedStudentIds());
            
            for (Student student : studentsToAssign) {
                // Set the group FK on the student
                student.setGroup(savedGroup);
            }
            // Save all modified students
            studentRepository.saveAll(studentsToAssign);
            
            // 4. Update the actual group size
            savedGroup.setGroupSize(studentsToAssign.size());
            groupRepository.save(savedGroup); // Save again to update the size
        } else {
            // If no students are selected, set size to 0 and save
            savedGroup.setGroupSize(0);
            groupRepository.save(savedGroup);
        }
    }
    
    // =========================================================
    // Existing Admin/User Management Methods
    // =========================================================
    // (Existing methods are kept below for context)

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