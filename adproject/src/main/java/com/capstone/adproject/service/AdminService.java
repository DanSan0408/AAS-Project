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
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.StudentRepository;

@Service
public class AdminService {

    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;
    private final IndustrialSupervisorRepository industrialSupervisorRepository;
    private final GroupRepository groupRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public AdminService(StudentRepository studentRepository, LecturerRepository lecturerRepository, IndustrialSupervisorRepository industrialSupervisorRepository, GroupRepository groupRepository, PasswordEncoder passwordEncoder) {
        this.studentRepository = studentRepository;
        this.lecturerRepository = lecturerRepository;
        this.industrialSupervisorRepository = industrialSupervisorRepository;
        this.groupRepository = groupRepository;
        this.passwordEncoder = passwordEncoder;
    }

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
        // 1. Create the new Group entity
        Group newGroup = new Group();
        newGroup.setGroupName(dto.getGroupName());

        // 2. Assign Supervisors/Lecturer
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
    
    @Transactional
    public void updateGroup(Long groupId, GroupAssignmentDto dto) {
        Group existingGroup = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found with ID: " + groupId));

        // 1. Update Group Name and Supervisors
        existingGroup.setGroupName(dto.getGroupName());
        lecturerRepository.findById(dto.getAcademicSupervisorId()).ifPresent(existingGroup::setAcademicSupervisor);
        industrialSupervisorRepository.findById(dto.getIndustrialSupervisorId()).ifPresent(existingGroup::setIndustrialSupervisor);
        
        // Save the group changes
        groupRepository.save(existingGroup);

        // 2. Add NEW Students to the existing group
        if (dto.getSelectedStudentIds() != null && !dto.getSelectedStudentIds().isEmpty()) {
            List<Student> newStudentsToAssign = studentRepository.findAllById(dto.getSelectedStudentIds());
            
            // Only students without a group will be added (prevent duplicates)
            List<Student> studentsToSave = newStudentsToAssign.stream()
                .filter(student -> student.getGroup() == null)
                .peek(student -> student.setGroup(existingGroup))
                .toList();
            
            studentRepository.saveAll(studentsToSave);
        }
        
        // 3. Recalculate and update the group size
        // This is necessary because students may have been added or removed in this or prior actions.
        // NOTE: This logic seems to be for *adding* students to an existing group, not for full edit/replacement. 
        // For randomization, we generally replace.
        // Keeping it for consistency with your existing updateGroup.
        long currentGroupSize = studentRepository.countByGroup(existingGroup); // Assuming you add countByGroup to StudentRepository
        existingGroup.setGroupSize((int) currentGroupSize);
        groupRepository.save(existingGroup);
    }
    
    @Transactional
    public void removeStudentFromGroup(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found with ID: " + studentId));
        
        Group group = student.getGroup();
        
        if (group != null) {
            student.setGroup(null); // Remove the student from the group
            studentRepository.save(student);
            
            // Recalculate and update group size
            long currentGroupSize = studentRepository.countByGroup(group);
            group.setGroupSize((int) currentGroupSize);
            groupRepository.save(group);
        }
    }
    
    @Transactional
    public void deleteGroupById(Long groupId) {
        // 1. Find the group or throw an exception if not found
        Group groupToDelete = groupRepository.findById(groupId)
            .orElseThrow(() -> new RuntimeException("Group not found with ID: " + groupId));
        
        // 2. Find all students currently assigned to this group (using the helper method or a direct repository call)
        List<Student> studentsToUnlink = studentRepository.findByGroup(groupToDelete);
        
        // 3. Unlink students from the group (set group FK to null)
        for (Student student : studentsToUnlink) {
            student.setGroup(null);
        }
        // Save all modified students
        studentRepository.saveAll(studentsToUnlink);
        
        // 4. Delete the group itself.
        groupRepository.delete(groupToDelete);
    }
    
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

    public void saveStudent(Student student) {
    if (student.getPassword() != null && !student.getPassword().isEmpty()) {
        student.setPassword(passwordEncoder.encode(student.getPassword()));
    } else {
        studentRepository.findById(student.getId()).ifPresent(existingStudent -> student.setPassword(existingStudent.getPassword()));
    }
    
    // START: Added logic to retain existing email if the submitted one is blank/null
    if (student.getId() != null && (student.getEmail() == null || student.getEmail().isEmpty())) {
        studentRepository.findById(student.getId()).ifPresent(existingStudent -> student.setEmail(existingStudent.getEmail()));
    }
    // END: Added logic
    
    studentRepository.save(student);
}

    public void saveLecturer(Lecturer lecturer) {
    if (lecturer.getPassword() != null && !lecturer.getPassword().isEmpty()) {
        lecturer.setPassword(passwordEncoder.encode(lecturer.getPassword()));
    } else {
        lecturerRepository.findById(lecturer.getId()).ifPresent(existingLecturer -> lecturer.setPassword(existingLecturer.getPassword()));
    }
    
    // START: Added logic to retain existing email if the submitted one is blank/null
    if (lecturer.getId() != null && (lecturer.getEmail() == null || lecturer.getEmail().isEmpty())) {
        lecturerRepository.findById(lecturer.getId()).ifPresent(existingLecturer -> lecturer.setEmail(existingLecturer.getEmail()));
    }
    // END: Added logic
    
    lecturerRepository.save(lecturer);
}
    public void saveIndustrialSupervisor(IndustrialSupervisor industrialSupervisor) {
    if (industrialSupervisor.getPassword() != null && !industrialSupervisor.getPassword().isEmpty()) {
        industrialSupervisor.setPassword(passwordEncoder.encode(industrialSupervisor.getPassword()));
    } else {
        industrialSupervisorRepository.findById(industrialSupervisor.getId()).ifPresent(existingSupervisor -> industrialSupervisor.setPassword(existingSupervisor.getPassword()));
    }

    // START: Added logic to retain existing email if the submitted one is blank/null
    if (industrialSupervisor.getId() != null && (industrialSupervisor.getEmail() == null || industrialSupervisor.getEmail().isEmpty())) {
        industrialSupervisorRepository.findById(industrialSupervisor.getId()).ifPresent(existingSupervisor -> industrialSupervisor.setEmail(existingSupervisor.getEmail()));
    }
    // END: Added logic
    
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

    public List<Student> searchStudentsWithoutGroup(String searchTerm) {
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            return studentRepository.findByGroupIsNullAndUsernameContainingIgnoreCase(searchTerm);
        }
        return studentRepository.findByGroupIsNull(); 
    }

    // REMOVED: public List<GroupAssignmentDto> generateRandomGroups(int numberOfGroups) {...}
    
    /**
     * NEW: Gathers all unassigned students into a single DTO for preview and editing.
     * The students are NOT shuffled, as shuffling is unnecessary if all are in one group.
     * @return A single GroupAssignmentDto with all unassigned student IDs.
     */
    public GroupAssignmentDto generateSingleRandomGroup() {
        List<Student> availableStudents = studentRepository.findByGroupIsNull();
        
        GroupAssignmentDto dto = new GroupAssignmentDto();
        dto.setGroupName("Random Group (Ready to Edit)"); // Default name
        
        List<Long> studentIds = availableStudents.stream()
                                         .map(Student::getId)
                                         .collect(Collectors.toList());

        dto.setSelectedStudentIds(studentIds);
        return dto;
    }

    // REMOVED: public void createGroupsFromDtoList(List<GroupAssignmentDto> groups) {...}

    /**
     * NEW: Persists a single GroupAssignmentDto object to the database.
     * @param group The single DTO representing the group to be created.
     * @return The number of groups created (1 or 0).
     */
    @Transactional
    public int createSingleGroupFromRandomization(GroupAssignmentDto group) {
        if (group == null) {
            return 0;
        }
        // Re-use the existing logic for creating and assigning a single group
        assignStudentsToNewGroup(group);
        return 1;
    }

    public long getAvailableStudentsCount() {
        // Assuming this method exists in StudentRepository (e.g., long countByGroupIsNull();)
        return studentRepository.countByGroupIsNull(); 
    }
    
    // REMOVED: public int createGroupsFromRandomization(List<GroupAssignmentDto> groups) {...}

    
}
