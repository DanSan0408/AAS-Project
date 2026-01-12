package com.capstone.adproject.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.dto.GroupAssignmentDto;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.IndustrialSupervisor;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.IndustrialSupervisorRepository;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.StudentRepository;

@Service
public class AdminService {

    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;
    private final IndustrialSupervisorRepository industrialSupervisorRepository;
    private final LecturerGroupAssignmentRepository assignmentRepository;
    private final GroupRepository groupRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public AdminService(
            StudentRepository studentRepository, 
            LecturerRepository lecturerRepository, 
            IndustrialSupervisorRepository industrialSupervisorRepository, 
            GroupRepository groupRepository, 
            PasswordEncoder passwordEncoder,
            LecturerGroupAssignmentRepository assignmentRepository) {
        this.studentRepository = studentRepository;
        this.lecturerRepository = lecturerRepository;
        this.industrialSupervisorRepository = industrialSupervisorRepository;
        this.groupRepository = groupRepository;
        this.passwordEncoder = passwordEncoder;
        this.assignmentRepository = assignmentRepository;
    }

    // ==========================================
    // 🔍 DUPLICATE CHECKERS (Whitespace Insensitive)
    // ==========================================
    
    public String checkStudentDuplicate(String username, String email, Long studentIdToExclude) {
        if (username != null) {
            String normalizedUsername = username.replaceAll("\\s+", "").toLowerCase();
            List<Student> allStudents = studentRepository.findAll();
            for (Student student : allStudents) {
                if (studentIdToExclude != null && student.getId().equals(studentIdToExclude)) continue;
                if (student.getUsername() != null) {
                    String existingNormalized = student.getUsername().replaceAll("\\s+", "").toLowerCase();
                    if (existingNormalized.equals(normalizedUsername)) {
                        return "Username '" + username + "' is similar to existing username '" + student.getUsername() + "' (ignoring spaces)";
                    }
                }
            }
        }
        if (email != null) {
            String normalizedEmail = email.replaceAll("\\s+", "").toLowerCase();
            List<Student> allStudents = studentRepository.findAll();
            for (Student student : allStudents) {
                if (studentIdToExclude != null && student.getId().equals(studentIdToExclude)) continue;
                if (student.getEmail() != null) {
                    String existingNormalized = student.getEmail().replaceAll("\\s+", "").toLowerCase();
                    if (existingNormalized.equals(normalizedEmail)) {
                        return "Email '" + email + "' is similar to existing email '" + student.getEmail() + "' (ignoring spaces)";
                    }
                }
            }
        }
        return null;
    }
    
    public String checkLecturerDuplicate(String username, String email, Long lecturerIdToExclude) {
        if (username != null) {
            String normalizedUsername = username.replaceAll("\\s+", "").toLowerCase();
            List<Lecturer> allLecturers = lecturerRepository.findAll();
            for (Lecturer lecturer : allLecturers) {
                if (lecturerIdToExclude != null && lecturer.getId().equals(lecturerIdToExclude)) continue;
                if (lecturer.getUsername() != null) {
                    String existingNormalized = lecturer.getUsername().replaceAll("\\s+", "").toLowerCase();
                    if (existingNormalized.equals(normalizedUsername)) {
                        return "Username '" + username + "' is similar to existing username '" + lecturer.getUsername() + "' (ignoring spaces)";
                    }
                }
            }
        }
        if (email != null) {
            String normalizedEmail = email.replaceAll("\\s+", "").toLowerCase();
            List<Lecturer> allLecturers = lecturerRepository.findAll();
            for (Lecturer lecturer : allLecturers) {
                if (lecturerIdToExclude != null && lecturer.getId().equals(lecturerIdToExclude)) continue;
                if (lecturer.getEmail() != null) {
                    String existingNormalized = lecturer.getEmail().replaceAll("\\s+", "").toLowerCase();
                    if (existingNormalized.equals(normalizedEmail)) {
                        return "Email '" + email + "' is similar to existing email '" + lecturer.getEmail() + "' (ignoring spaces)";
                    }
                }
            }
        }
        return null;
    }
    
    public String checkSupervisorDuplicate(String username, String email, Long supervisorIdToExclude) {
        if (username != null) {
            String normalizedUsername = username.replaceAll("\\s+", "").toLowerCase();
            List<IndustrialSupervisor> allSupervisors = industrialSupervisorRepository.findAll();
            for (IndustrialSupervisor supervisor : allSupervisors) {
                if (supervisorIdToExclude != null && supervisor.getId().equals(supervisorIdToExclude)) continue;
                if (supervisor.getUsername() != null) {
                    String existingNormalized = supervisor.getUsername().replaceAll("\\s+", "").toLowerCase();
                    if (existingNormalized.equals(normalizedUsername)) {
                        return "Username '" + username + "' is similar to existing username '" + supervisor.getUsername() + "' (ignoring spaces)";
                    }
                }
            }
        }
        if (email != null) {
            String normalizedEmail = email.replaceAll("\\s+", "").toLowerCase();
            List<IndustrialSupervisor> allSupervisors = industrialSupervisorRepository.findAll();
            for (IndustrialSupervisor supervisor : allSupervisors) {
                if (supervisorIdToExclude != null && supervisor.getId().equals(supervisorIdToExclude)) continue;
                if (supervisor.getEmail() != null) {
                    String existingNormalized = supervisor.getEmail().replaceAll("\\s+", "").toLowerCase();
                    if (existingNormalized.equals(normalizedEmail)) {
                        return "Email '" + email + "' is similar to existing email '" + supervisor.getEmail() + "' (ignoring spaces)";
                    }
                }
            }
        }
        return null;
    }

    // ==========================================
    // 💾 SAVE METHODS (With Validation Fixes)
    // ==========================================

    public void saveStudent(Student student) {
        // 1. Validate Required Fields
        if (student.getUsername() == null || student.getUsername().trim().isEmpty()) {
            throw new RuntimeException("Username is required");
        }
        if (student.getEmail() == null || student.getEmail().trim().isEmpty()) {
            throw new RuntimeException("Email is required");
        }

        if (student.getId() != null) {
            // UPDATE Logic
            Student existingStudent = studentRepository.findById(student.getId())
                .orElseThrow(() -> new RuntimeException("Student not found"));
            
            // If password field is empty, keep the old password
            if (student.getPassword() == null || student.getPassword().trim().isEmpty()) {
                student.setPassword(existingStudent.getPassword());
            } else {
                student.setPassword(passwordEncoder.encode(student.getPassword()));
            }
            
            // Fallback for email if empty (though validation above catches it mostly)
            if (student.getEmail() == null || student.getEmail().trim().isEmpty()) {
                student.setEmail(existingStudent.getEmail());
            }
        } else {
            // CREATE Logic
            if (student.getPassword() == null || student.getPassword().trim().isEmpty()) {
                throw new RuntimeException("Password is required for new students");
            }
            student.setPassword(passwordEncoder.encode(student.getPassword()));
        }
        
        studentRepository.save(student);
    }

    public void saveLecturer(Lecturer lecturer) {
        // 1. Validate Required Fields
        if (lecturer.getUsername() == null || lecturer.getUsername().trim().isEmpty()) {
            throw new RuntimeException("Username is required");
        }
        if (lecturer.getEmail() == null || lecturer.getEmail().trim().isEmpty()) {
            throw new RuntimeException("Email is required");
        }

        if (lecturer.getId() != null) {
            // UPDATE Logic
            Lecturer existingLecturer = lecturerRepository.findById(lecturer.getId())
                .orElseThrow(() -> new RuntimeException("Lecturer not found"));
            
            if (lecturer.getPassword() == null || lecturer.getPassword().trim().isEmpty()) {
                lecturer.setPassword(existingLecturer.getPassword());
            } else {
                lecturer.setPassword(passwordEncoder.encode(lecturer.getPassword()));
            }
            
            if (lecturer.getEmail() == null || lecturer.getEmail().trim().isEmpty()) {
                lecturer.setEmail(existingLecturer.getEmail());
            }
        } else {
            // CREATE Logic
            if (lecturer.getPassword() == null || lecturer.getPassword().trim().isEmpty()) {
                throw new RuntimeException("Password is required for new lecturers");
            }
            lecturer.setPassword(passwordEncoder.encode(lecturer.getPassword()));
        }
        
        lecturerRepository.save(lecturer);
    }

    public void saveIndustrialSupervisor(IndustrialSupervisor industrialSupervisor) {
        // 1. Validate Required Fields
        if (industrialSupervisor.getUsername() == null || industrialSupervisor.getUsername().trim().isEmpty()) {
            throw new RuntimeException("Username is required");
        }
        if (industrialSupervisor.getEmail() == null || industrialSupervisor.getEmail().trim().isEmpty()) {
            throw new RuntimeException("Email is required");
        }

        if (industrialSupervisor.getId() != null) {
            // UPDATE Logic
            IndustrialSupervisor existingSupervisor = industrialSupervisorRepository.findById(industrialSupervisor.getId())
                .orElseThrow(() -> new RuntimeException("Supervisor not found"));
            
            if (industrialSupervisor.getPassword() == null || industrialSupervisor.getPassword().trim().isEmpty()) {
                industrialSupervisor.setPassword(existingSupervisor.getPassword());
            } else {
                industrialSupervisor.setPassword(passwordEncoder.encode(industrialSupervisor.getPassword()));
            }
            
            if (industrialSupervisor.getEmail() == null || industrialSupervisor.getEmail().trim().isEmpty()) {
                industrialSupervisor.setEmail(existingSupervisor.getEmail());
            }
        } else {
            // CREATE Logic
            if (industrialSupervisor.getPassword() == null || industrialSupervisor.getPassword().trim().isEmpty()) {
                throw new RuntimeException("Password is required for new supervisors");
            }
            industrialSupervisor.setPassword(passwordEncoder.encode(industrialSupervisor.getPassword()));
        }
        
        industrialSupervisorRepository.save(industrialSupervisor);
    }

    // ==========================================
    // 🗑️ DELETE METHODS (Safe & Transactional)
    // ==========================================

    @Transactional
    public void deleteStudentById(Long id) {
        Student student = studentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Student not found"));
        
        // 1. Remove from Group 
        if (student.getGroup() != null) {
            removeStudentFromGroup(id);
        }

        // 2. Cleanup dependencies
        studentRepository.deleteCalculatedResultsByStudentId(id);
        studentRepository.deleteCommentsByStudentId(id);
        studentRepository.deleteMarksReceivedByStudent(id);
        studentRepository.deleteMarksGivenByStudent(id);
        studentRepository.deleteOverridesByStudent(id);

        // 3. Delete Student
        studentRepository.delete(student);
    }

    @Transactional
    public void deleteLecturerById(Long id) {
        Lecturer lecturer = lecturerRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Lecturer not found"));

        // 1. Unlink from Groups (set academic_supervisor_id = NULL)
        lecturerRepository.unlinkFromGroups(id);

        // 2. Delete assignments (LecturerGroupAssignment)
        lecturerRepository.deleteGroupAssignments(id);

        // 3. Delete Marks given by this lecturer
        lecturerRepository.deleteMarksGiven(id);
        
        // 4. Delete comments given by this lecturer
        lecturerRepository.deleteCommentsByLecturer(id);

        // 5. Delete Lecturer
        lecturerRepository.delete(lecturer);
    }

    @Transactional
    public void deleteIndustrialSupervisorById(Long id) {
        IndustrialSupervisor supervisor = industrialSupervisorRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Supervisor not found"));

        // 1. Unlink from Groups (set industrial_supervisor_id = NULL)
        industrialSupervisorRepository.unlinkFromGroups(id);

        // 2. Delete Marks given by this supervisor
        industrialSupervisorRepository.deleteMarksGiven(id);
        
        // 3. Delete comments given by this supervisor
        industrialSupervisorRepository.deleteCommentsBySupervisor(id);

        // 4. Delete Supervisor
        industrialSupervisorRepository.delete(supervisor);
    }

    @Transactional
    public void deleteStudentsByIds(List<Long> ids) {
        if (ids == null) return;
        for (Long id : ids) {
            deleteStudentById(id);
        }
    }

    @Transactional
    public void deleteLecturersByIds(List<Long> ids) {
        if (ids == null) return;
        for (Long id : ids) {
            deleteLecturerById(id);
        }
    }

    @Transactional
    public void deleteIndustrialSupervisorsByIds(List<Long> ids) {
        if (ids == null) return;
        for (Long id : ids) {
            deleteIndustrialSupervisorById(id);
        }
    }

    // ==========================================
    // 👥 GROUP MANAGEMENT
    // ==========================================

    public List<Student> getStudentsWithoutGroup() {
        return studentRepository.findByGroupIsNull();
    }
    
    public List<Student> getStudentsByGroup(Group group) {
        return studentRepository.findByGroup(group); 
    }
    
    public List<Group> getAllGroups() {
        return groupRepository.findAll();
    }
    
    public Optional<Group> findGroupById(Long id) {
        return groupRepository.findById(id);
    }

    @Transactional
    public void assignStudentsToNewGroup(GroupAssignmentDto dto) {
        Group newGroup = new Group();
        newGroup.setGroupName(dto.getGroupName());

        lecturerRepository.findById(dto.getAcademicSupervisorId()).ifPresent(newGroup::setAcademicSupervisor);
        industrialSupervisorRepository.findById(dto.getIndustrialSupervisorId()).ifPresent(newGroup::setIndustrialSupervisor);

        Group savedGroup = groupRepository.save(newGroup);

        if (dto.getSelectedStudentIds() != null && !dto.getSelectedStudentIds().isEmpty()) {
            List<Student> studentsToAssign = studentRepository.findAllById(dto.getSelectedStudentIds());

            for (Student student : studentsToAssign) {
                student.setGroup(savedGroup);
            }
            studentRepository.saveAll(studentsToAssign);

            savedGroup.setGroupSize(studentsToAssign.size());
            groupRepository.save(savedGroup);
        } else {
            savedGroup.setGroupSize(0);
            groupRepository.save(savedGroup);
        }
    }
    
    @Transactional
    public void updateGroup(Long groupId, GroupAssignmentDto dto) {
        Group existingGroup = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found with ID: " + groupId));

        existingGroup.setGroupName(dto.getGroupName());
        
        if (dto.getAcademicSupervisorId() != null) {
            lecturerRepository.findById(dto.getAcademicSupervisorId()).ifPresent(existingGroup::setAcademicSupervisor);
        } else {
            existingGroup.setAcademicSupervisor(null);
        }
        
        if (dto.getIndustrialSupervisorId() != null) {
            industrialSupervisorRepository.findById(dto.getIndustrialSupervisorId()).ifPresent(existingGroup::setIndustrialSupervisor);
        } else {
            existingGroup.setIndustrialSupervisor(null);
        }
        
        groupRepository.save(existingGroup);

        if (dto.getSelectedStudentIds() != null && !dto.getSelectedStudentIds().isEmpty()) {
            List<Student> newStudentsToAssign = studentRepository.findAllById(dto.getSelectedStudentIds());
            
            List<Student> studentsToSave = newStudentsToAssign.stream()
                .filter(student -> student.getGroup() == null)
                .peek(student -> student.setGroup(existingGroup))
                .toList();
            
            studentRepository.saveAll(studentsToSave);
        }
        
        long currentGroupSize = studentRepository.countByGroup(existingGroup);
        existingGroup.setGroupSize((int) currentGroupSize);
        groupRepository.save(existingGroup);
    }
    
    @Transactional
    public void removeStudentFromGroup(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found with ID: " + studentId));
        
        Group group = student.getGroup();
        
        if (group != null) {
            student.setGroup(null);
            studentRepository.save(student);
            
            long currentGroupSize = studentRepository.countByGroup(group);
            group.setGroupSize((int) currentGroupSize);
            groupRepository.save(group);
        }
    }
    
    @Transactional
    public void deleteGroupById(Long groupId) {
        Group groupToDelete = groupRepository.findById(groupId)
            .orElseThrow(() -> new RuntimeException("Group not found with ID: " + groupId));
        
        List<Student> studentsToUnlink = studentRepository.findByGroup(groupToDelete);
        for (Student student : studentsToUnlink) {
            student.setGroup(null);
        }
        studentRepository.saveAll(studentsToUnlink);
        
        assignmentRepository.deleteByGroup(groupToDelete);
        assignmentRepository.flush(); 
        
        groupRepository.delete(groupToDelete);
    }

    // ==========================================
    // 🔍 SEARCH & HELPERS
    // ==========================================

    public List<Student> getAllStudents() {
        return studentRepository.findAllWithGroupEagerly();
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

    public List<Student> searchStudentsWithoutGroup(String searchTerm) {
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            return studentRepository.findByGroupIsNullAndUsernameContainingIgnoreCase(searchTerm);
        }
        return studentRepository.findByGroupIsNull(); 
    }

    public GroupAssignmentDto generateSingleRandomGroup() {
        List<Student> availableStudents = studentRepository.findByGroupIsNull();
        
        GroupAssignmentDto dto = new GroupAssignmentDto();
        dto.setGroupName("Random Group (Ready to Edit)");
        
        List<Long> studentIds = availableStudents.stream()
                                                 .map(Student::getId)
                                                 .collect(Collectors.toList());

        dto.setSelectedStudentIds(studentIds);
        return dto;
    }

    @Transactional
    public int createSingleGroupFromRandomization(GroupAssignmentDto group) {
        if (group == null) {
            return 0;
        }
        assignStudentsToNewGroup(group);
        return 1;
    }

    public long getAvailableStudentsCount() {
        return studentRepository.countByGroupIsNull(); 
    }
}