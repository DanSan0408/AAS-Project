package com.capstone.adproject.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
import com.capstone.adproject.util.PasswordGenerator;

import jakarta.servlet.http.HttpServletRequest;

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

    @Autowired
private EmailService emailService;

    public void saveStudent(Student student, HttpServletRequest request) {
    if (student.getEmail() == null || student.getEmail().trim().isEmpty()) {
        throw new RuntimeException("Email is required");
    }

    if (student.getId() != null) {
        Student existingStudent = studentRepository.findById(student.getId())
            .orElseThrow(() -> new RuntimeException("Student not found"));
        
        student.setPassword(existingStudent.getPassword());
        student.setIsTempPassword(existingStudent.getIsTempPassword());
        student.setUsername(existingStudent.getUsername()); 
        student.setResetPasswordToken(existingStudent.getResetPasswordToken());
        
        if (!existingStudent.getEmail().equals(student.getEmail())) {
            student.setEmail(student.getEmail());
        }
        
    } else {

        String tempPassword = PasswordGenerator.generateRandomPassword();
        student.setPassword(passwordEncoder.encode(tempPassword));
        student.setIsTempPassword(true);
        student.setUsername(null); 
        
        String resetToken = UUID.randomUUID().toString();
        student.setResetPasswordToken(resetToken);
        
        Student savedStudent = studentRepository.save(student);
        
        String applicationUrl = request.getScheme() + "://" + request.getServerName();
        if (request.getServerPort() != 80 && request.getServerPort() != 443) {
            applicationUrl += ":" + request.getServerPort();
        }
        String resetLink = applicationUrl + request.getContextPath() + "/reset_password?token=" + resetToken;
        
        try {
            emailService.sendWelcomeEmailWithPassword(
                student.getEmail(),
                tempPassword,
                "Student",
                resetLink
            );
        } catch (Exception e) {
            System.err.println("Failed to send welcome email to: " + student.getEmail());
            e.printStackTrace();
        }
        
        return; 
    }
    
    studentRepository.save(student);
}

public void saveLecturer(Lecturer lecturer, HttpServletRequest request) {
    if (lecturer.getEmail() == null || lecturer.getEmail().trim().isEmpty()) {
        throw new RuntimeException("Email is required");
    }

    if (lecturer.getId() != null) {
        Lecturer existingLecturer = lecturerRepository.findById(lecturer.getId())
            .orElseThrow(() -> new RuntimeException("Lecturer not found"));
        
        lecturer.setPassword(existingLecturer.getPassword());
        lecturer.setIsTempPassword(existingLecturer.getIsTempPassword());
        lecturer.setUsername(existingLecturer.getUsername());
        lecturer.setResetPasswordToken(existingLecturer.getResetPasswordToken());
        
    } else {
        String tempPassword = PasswordGenerator.generateRandomPassword();
        lecturer.setPassword(passwordEncoder.encode(tempPassword));
        lecturer.setIsTempPassword(true);
        lecturer.setUsername(null);
        
        String resetToken = UUID.randomUUID().toString();
        lecturer.setResetPasswordToken(resetToken);
        
        Lecturer savedLecturer = lecturerRepository.save(lecturer);
        
        String applicationUrl = request.getScheme() + "://" + request.getServerName();
        if (request.getServerPort() != 80 && request.getServerPort() != 443) {
            applicationUrl += ":" + request.getServerPort();
        }
        String resetLink = applicationUrl + request.getContextPath() + "/reset_password?token=" + resetToken;
        
        try {
            emailService.sendWelcomeEmailWithPassword(
                lecturer.getEmail(),
                tempPassword,
                "Lecturer",
                resetLink
            );
        } catch (Exception e) {
            System.err.println("Failed to send welcome email to: " + lecturer.getEmail());
            e.printStackTrace();
        }
        
        return;
    }
    
    lecturerRepository.save(lecturer);
}

public void saveIndustrialSupervisor(IndustrialSupervisor supervisor, HttpServletRequest request) {
    if (supervisor.getEmail() == null || supervisor.getEmail().trim().isEmpty()) {
        throw new RuntimeException("Email is required");
    }

    if (supervisor.getId() != null) {
        // UPDATE
        IndustrialSupervisor existing = industrialSupervisorRepository.findById(supervisor.getId())
            .orElseThrow(() -> new RuntimeException("Supervisor not found"));
        
        supervisor.setPassword(existing.getPassword());
        supervisor.setIsTempPassword(existing.getIsTempPassword());
        supervisor.setUsername(existing.getUsername());
        supervisor.setResetPasswordToken(existing.getResetPasswordToken());
        
    } else {
        String tempPassword = PasswordGenerator.generateRandomPassword();
        supervisor.setPassword(passwordEncoder.encode(tempPassword));
        supervisor.setIsTempPassword(true);
        supervisor.setUsername(null);
        
        String resetToken = UUID.randomUUID().toString();
        supervisor.setResetPasswordToken(resetToken);
        
        IndustrialSupervisor saved = industrialSupervisorRepository.save(supervisor);
        
        String applicationUrl = request.getScheme() + "://" + request.getServerName();
        if (request.getServerPort() != 80 && request.getServerPort() != 443) {
            applicationUrl += ":" + request.getServerPort();
        }
        String resetLink = applicationUrl + request.getContextPath() + "/reset_password?token=" + resetToken;
        
        try {
            emailService.sendWelcomeEmailWithPassword(
                supervisor.getEmail(),
                tempPassword,
                "Industrial Supervisor",
                resetLink
            );
        } catch (Exception e) {
            System.err.println("Failed to send welcome email to: " + supervisor.getEmail());
            e.printStackTrace();
        }
        
        return;
    }
    
    industrialSupervisorRepository.save(supervisor);
}

public String checkStudentEmailDuplicate(String email, Long studentIdToExclude) {
    if (email == null || email.trim().isEmpty()) {
        return "Email is required";
    }
    
    String normalizedEmail = email.replaceAll("\\s+", "").toLowerCase();
    List<Student> allStudents = studentRepository.findAll();
    
    for (Student student : allStudents) {
        if (studentIdToExclude != null && student.getId().equals(studentIdToExclude)) continue;
        if (student.getEmail() != null) {
            String existingNormalized = student.getEmail().replaceAll("\\s+", "").toLowerCase();
            if (existingNormalized.equals(normalizedEmail)) {
                return "Email '" + email + "' is already registered";
            }
        }
    }
    return null;
}

public String checkLecturerEmailDuplicate(String email, Long lecturerIdToExclude) {
    if (email == null || email.trim().isEmpty()) {
        return "Email is required";
    }
    
    String normalizedEmail = email.replaceAll("\\s+", "").toLowerCase();
    List<Lecturer> allLecturers = lecturerRepository.findAll();
    
    for (Lecturer lecturer : allLecturers) {
        if (lecturerIdToExclude != null && lecturer.getId().equals(lecturerIdToExclude)) continue;
        if (lecturer.getEmail() != null) {
            String existingNormalized = lecturer.getEmail().replaceAll("\\s+", "").toLowerCase();
            if (existingNormalized.equals(normalizedEmail)) {
                return "Email '" + email + "' is already registered";
            }
        }
    }
    return null;
}

public String checkSupervisorEmailDuplicate(String email, Long supervisorIdToExclude) {
    if (email == null || email.trim().isEmpty()) {
        return "Email is required";
    }
    
    String normalizedEmail = email.replaceAll("\\s+", "").toLowerCase();
    List<IndustrialSupervisor> allSupervisors = industrialSupervisorRepository.findAll();
    
    for (IndustrialSupervisor supervisor : allSupervisors) {
        if (supervisorIdToExclude != null && supervisor.getId().equals(supervisorIdToExclude)) continue;
        if (supervisor.getEmail() != null) {
            String existingNormalized = supervisor.getEmail().replaceAll("\\s+", "").toLowerCase();
            if (existingNormalized.equals(normalizedEmail)) {
                return "Email '" + email + "' is already registered";
            }
        }
    }
    return null;
}

    @Transactional
    public void deleteStudentById(Long id) {
        Student student = studentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Student not found"));
        
        if (student.getGroup() != null) {
            removeStudentFromGroup(id);
        }

        studentRepository.deleteCalculatedResultsByStudentId(id);
        studentRepository.deleteCommentsByStudentId(id);
        studentRepository.deleteMarksReceivedByStudent(id);
        studentRepository.deleteMarksGivenByStudent(id);
        studentRepository.deleteOverridesByStudent(id);

        studentRepository.delete(student);
    }

    @Transactional
    public void deleteLecturerById(Long id) {
        Lecturer lecturer = lecturerRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Lecturer not found"));

        lecturerRepository.unlinkFromGroups(id);

        lecturerRepository.deleteGroupAssignments(id);

        lecturerRepository.deleteMarksGiven(id);
        
        lecturerRepository.deleteCommentsByLecturer(id);

        lecturerRepository.delete(lecturer);
    }

    @Transactional
    public void deleteIndustrialSupervisorById(Long id) {
        IndustrialSupervisor supervisor = industrialSupervisorRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Supervisor not found"));

        industrialSupervisorRepository.unlinkFromGroups(id);

        industrialSupervisorRepository.deleteMarksGiven(id);
        
        industrialSupervisorRepository.deleteCommentsBySupervisor(id);

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