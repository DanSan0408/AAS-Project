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
import com.capstone.adproject.model.Admin;
import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.repositories.AdminRepository;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.StudentRepository;
import com.capstone.adproject.util.PasswordGenerator;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class AdminService {

    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;
    private final LecturerGroupAssignmentRepository assignmentRepository;
    private final GroupRepository groupRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminRepository adminRepository;
    private final CourseScopeService courseScopeService;

    @Autowired
    public AdminService(
            StudentRepository studentRepository, 
            LecturerRepository lecturerRepository, 
            GroupRepository groupRepository, 
            PasswordEncoder passwordEncoder,
            LecturerGroupAssignmentRepository assignmentRepository,
            AdminRepository adminRepository,
            CourseScopeService courseScopeService) {
        this.studentRepository = studentRepository;
        this.lecturerRepository = lecturerRepository;
        this.groupRepository = groupRepository;
        this.passwordEncoder = passwordEncoder;
        this.assignmentRepository = assignmentRepository;
        this.adminRepository = adminRepository;
        this.courseScopeService = courseScopeService;
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
        student.setCourse(existingStudent.getCourse());
        
        if (!existingStudent.getEmail().equals(student.getEmail())) {
            student.setEmail(student.getEmail());
        }
        
    } else {
        Course activeCourse = resolveCurrentAdminCourse()
            .orElseThrow(() -> new RuntimeException("No active course selected for student creation"));
        student.setCourse(activeCourse);

        // Check if user already exists in other tables to sync password
        String existingPassword = null;
        Boolean isTemp = true;
        String resetToken = UUID.randomUUID().toString();
        
        Optional<Admin> existingAdmin = adminRepository.findByEmail(student.getEmail());
        if (existingAdmin.isPresent()) {
            existingPassword = existingAdmin.get().getPassword();
            isTemp = false;
            resetToken = existingAdmin.get().getResetPasswordToken();
        }
        
        Optional<Lecturer> existingLecturer = lecturerRepository.findByEmail(student.getEmail());
        if (existingLecturer.isPresent()) {
            existingPassword = existingLecturer.get().getPassword();
            isTemp = existingLecturer.get().getIsTempPassword();
            resetToken = existingLecturer.get().getResetPasswordToken();
        }

        if (existingPassword != null) {
            student.setPassword(existingPassword);
            student.setIsTempPassword(isTemp);
            student.setResetPasswordToken(resetToken);
        } else {
            String tempPassword = PasswordGenerator.generateRandomPassword();
            student.setPassword(passwordEncoder.encode(tempPassword));
            student.setIsTempPassword(true);
            student.setResetPasswordToken(resetToken);
            
            sendWelcomeEmail(student.getEmail(), tempPassword, "Student", resetToken, request);
        }
        
        if (student.getUsername() == null || student.getUsername().trim().isEmpty()) {
            student.setUsername(student.getEmail().split("@")[0]);
        }
        studentRepository.save(student);
        return; 
    }
    
    studentRepository.save(student);
}

private Optional<Course> resolveCurrentAdminCourse() {
    Course activeCourse = courseScopeService.getActiveCourseForCurrentUser();
    if (activeCourse != null && activeCourse.getId() != null) {
        return Optional.of(activeCourse);
    }

    List<Course> managed = courseScopeService.getManagedCoursesForCurrentUser();
    if (!managed.isEmpty()) {
        return Optional.of(managed.get(0));
    }

    return Optional.empty();
}

public Optional<Course> getCurrentAdminCourse() {
    return resolveCurrentAdminCourse();
}

private void sendWelcomeEmail(String email, String tempPassword, String role, String resetToken, HttpServletRequest request) {
    String applicationUrl = request.getScheme() + "://" + request.getServerName();
    if (request.getServerPort() != 80 && request.getServerPort() != 443) {
        applicationUrl += ":" + request.getServerPort();
    }
    String resetLink = applicationUrl + request.getContextPath() + "/reset_password?token=" + resetToken;
    
    try {
        emailService.sendWelcomeEmailWithPassword(
            email,
            tempPassword,
            role,
            resetLink
        );
    } catch (Exception e) {
        System.err.println("Failed to send welcome email to: " + email);
        e.printStackTrace();
    }
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
        lecturer.setRoles(existingLecturer.getRoles()); // Preserve existing roles
        lecturer.setCourse(existingLecturer.getCourse());
        
    } else {
        Course activeCourse = resolveCurrentAdminCourse()
            .orElseThrow(() -> new RuntimeException("No active course selected for lecturer creation"));
        lecturer.setCourse(activeCourse);

        // Check if user already exists in other tables to sync password
        String existingPassword = null;
        Boolean isTemp = true;
        String resetToken = UUID.randomUUID().toString();
        
        Optional<Admin> existingAdmin = adminRepository.findByEmail(lecturer.getEmail());
        if (existingAdmin.isPresent()) {
            existingPassword = existingAdmin.get().getPassword();
            isTemp = false;
            resetToken = existingAdmin.get().getResetPasswordToken();
        }

        if (existingPassword != null) {
            lecturer.setPassword(existingPassword);
            lecturer.setIsTempPassword(isTemp);
            lecturer.setResetPasswordToken(resetToken);
        } else {
            String tempPassword = PasswordGenerator.generateRandomPassword();
            lecturer.setPassword(passwordEncoder.encode(tempPassword));
            lecturer.setIsTempPassword(true);
            lecturer.setResetPasswordToken(resetToken);
            lecturer.setRoles("ROLE_LECTURER"); // Default role for new lecturer
            
            sendWelcomeEmail(lecturer.getEmail(), tempPassword, "Lecturer", resetToken, request);
        }
        
        if (lecturer.getUsername() == null || lecturer.getUsername().trim().isEmpty()) {
            lecturer.setUsername(lecturer.getEmail().split("@")[0]);
        }
        lecturerRepository.save(lecturer);
        return;
    }
    
    lecturerRepository.save(lecturer);
}


@Transactional
public int bulkAddStudents(String emailsText, HttpServletRequest request) {
    String[] emails = emailsText.split("[,\\n\\r]+");
    int count = 0;
    for (String email : emails) {
        String trimmedEmail = email.trim().toLowerCase();
        if (!trimmedEmail.isEmpty()) {
            if (studentRepository.findByEmail(trimmedEmail).isEmpty()) {
                Student student = new Student();
                student.setEmail(trimmedEmail);
                saveStudent(student, request);
                count++;
            }
        }
    }
    return count;
}

@Transactional
public int bulkAddLecturers(String emailsText, HttpServletRequest request) {
    String[] emails = emailsText.split("[,\\n\\r]+");
    int count = 0;
    for (String email : emails) {
        String trimmedEmail = email.trim().toLowerCase();
        if (!trimmedEmail.isEmpty()) {
            if (lecturerRepository.findByEmail(trimmedEmail).isEmpty()) {
                Lecturer lecturer = new Lecturer();
                lecturer.setEmail(trimmedEmail);
                saveLecturer(lecturer, request);
                count++;
            }
        }
    }
    return count;
}

public String checkStudentEmailDuplicate(String email, Long studentIdToExclude) {    if (email == null || email.trim().isEmpty()) {
        return "Email is required";
    }
    
    String normalizedEmail = email.replaceAll("\\s+", "").toLowerCase();
    List<Student> allStudents = studentRepository.findAll();
    
    for (Student student : allStudents) {
        if (studentIdToExclude != null && student.getId().equals(studentIdToExclude)) continue;
        if (student.getEmail() != null) {
            String existingNormalized = student.getEmail().replaceAll("\\s+", "").toLowerCase();
            if (existingNormalized.equals(normalizedEmail)) {
                return "Email '" + email + "' is already registered as a Student.";
            }
        }
    }
    return null;
}

private Optional<Long> getActiveCourseId() {
    return Optional.ofNullable(courseScopeService.getActiveCourseIdForCurrentUser());
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
                return "Email '" + email + "' is already registered as a Lecturer.";
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


    public List<Student> getStudentsWithoutGroup() {
        return getActiveCourseId()
            .map(studentRepository::findByCourseIdAndGroupIsNull)
            .orElse(List.of());
    }
    
    public List<Student> getStudentsByGroup(Group group) {
        return studentRepository.findByGroup(group); 
    }
    
    public List<Group> getAllGroups() {
        return getActiveCourseId()
            .map(groupRepository::findByCourseId)
            .orElse(List.of());
    }
    
    public Optional<Group> findGroupById(Long id) {
        return groupRepository.findById(id);
    }

    @Transactional
    public void assignStudentsToNewGroup(GroupAssignmentDto dto) {
        Group newGroup = new Group();
        String groupName = dto.getGroupName();
        if (groupName == null || groupName.trim().isEmpty()) {
            groupName = "Random Group " + java.util.UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        }
        newGroup.setGroupName(groupName);
        resolveCurrentAdminCourse().ifPresent(newGroup::setCourse);

        if (dto.getAcademicSupervisorId() != null) {
            lecturerRepository.findById(dto.getAcademicSupervisorId()).ifPresent(newGroup::setAcademicSupervisor);
        }
        if (dto.getIndustrialSupervisorId() != null) {
            lecturerRepository.findById(dto.getIndustrialSupervisorId()).ifPresent(newGroup::setIndustrialSupervisor);
        }

        Group savedGroup = groupRepository.save(newGroup);

        if (dto.getSelectedStudentIds() != null && !dto.getSelectedStudentIds().isEmpty()) {
            List<Student> studentsToAssign = studentRepository.findAllById(dto.getSelectedStudentIds());

            if (newGroup.getCourse() == null) {
                studentsToAssign.stream()
                        .map(Student::getCourse)
                        .filter(c -> c != null)
                        .findFirst()
                        .ifPresent(newGroup::setCourse);
            }

            groupRepository.save(savedGroup);

            for (Student student : studentsToAssign) {
                student.setGroup(savedGroup);
                if (student.getCourse() == null && savedGroup.getCourse() != null) {
                    student.setCourse(savedGroup.getCourse());
                }
                if (student.getUsername() == null || student.getUsername().trim().isEmpty()) {
                    student.setUsername(student.getEmail() != null ? student.getEmail().split("@")[0] : "student" + student.getId());
                }
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

        if (existingGroup.getCourse() == null) {
            resolveCurrentAdminCourse().ifPresent(existingGroup::setCourse);
        }

        existingGroup.setGroupName(dto.getGroupName());
        
        if (dto.getAcademicSupervisorId() != null) {
            lecturerRepository.findById(dto.getAcademicSupervisorId()).ifPresent(existingGroup::setAcademicSupervisor);
        } else {
            existingGroup.setAcademicSupervisor(null);
        }
        
        if (dto.getIndustrialSupervisorId() != null) {
            lecturerRepository.findById(dto.getIndustrialSupervisorId()).ifPresent(existingGroup::setIndustrialSupervisor);
        } else {
            existingGroup.setIndustrialSupervisor(null);
        }
        
        groupRepository.save(existingGroup);

        if (dto.getSelectedStudentIds() != null && !dto.getSelectedStudentIds().isEmpty()) {
            List<Student> newStudentsToAssign = studentRepository.findAllById(dto.getSelectedStudentIds());
            
            List<Student> studentsToSave = newStudentsToAssign.stream()
                .filter(student -> student.getGroup() == null)
                .peek(student -> {
                    student.setGroup(existingGroup);
                    if (student.getCourse() == null && existingGroup.getCourse() != null) {
                        student.setCourse(existingGroup.getCourse());
                    }
                })
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
        return getActiveCourseId()
            .map(studentRepository::findAllWithGroupEagerlyByCourseId)
            .orElse(List.of());
    }

    public List<Lecturer> getAllLecturers() {
        return getActiveCourseId()
            .map(lecturerRepository::findByCourseId)
            .orElse(List.of());
    }


    public Optional<Student> findStudentById(Long id) {
        return studentRepository.findById(id);
    }

    public Optional<Lecturer> findLecturerById(Long id) {
        return lecturerRepository.findById(id);
    }


    public List<Student> searchStudentsWithoutGroup(String searchTerm) {
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            return getStudentsWithoutGroup().stream()
                .filter(student -> student.getUsername() != null
                    && student.getUsername().toLowerCase().contains(searchTerm.toLowerCase()))
                .toList();
        }
        return getStudentsWithoutGroup(); 
    }

    public GroupAssignmentDto generateSingleRandomGroup() {
        List<Student> availableStudents = getStudentsWithoutGroup();
        
        GroupAssignmentDto dto = new GroupAssignmentDto();
        String uniqueId = java.util.UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        dto.setGroupName("Random Group " + uniqueId);
        
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
        return getActiveCourseId()
            .map(studentRepository::countByCourseIdAndGroupIsNull)
            .orElse(0L); 
    }

    public Optional<Admin> findAdminByEmail(String email) {
        return adminRepository.findByEmail(email);
    }

    @Transactional
    public void addRole(String email, String targetRole, HttpServletRequest request) {
        if (email == null || email.trim().isEmpty()) {
            throw new RuntimeException("Email is required for role assignation");
        }

        String normalizedEmail = email.trim().toLowerCase();

        if ("LECTURER".equalsIgnoreCase(targetRole)) {
            Optional<Lecturer> existingLecturerOpt = lecturerRepository.findByEmail(normalizedEmail);
            Lecturer lecturerToUpdate;
            if (existingLecturerOpt.isPresent()) {
                lecturerToUpdate = existingLecturerOpt.get();
                String currentRoles = lecturerToUpdate.getRoles() != null ? lecturerToUpdate.getRoles() : "";
                if (!currentRoles.contains("ROLE_LECTURER")) {
                    lecturerToUpdate.setRoles(currentRoles.isEmpty() ? "ROLE_LECTURER" : currentRoles + ",ROLE_LECTURER");
                }
            } else {
                lecturerToUpdate = new Lecturer();
                lecturerToUpdate.setEmail(normalizedEmail);
                lecturerToUpdate.setRoles("ROLE_LECTURER");
                // Password and temp password will be handled by saveLecturer
            }
            saveLecturer(lecturerToUpdate, request);
        } else {
            throw new RuntimeException("Invalid target role: " + targetRole + ". Only LECTURER role is supported.");
        }
    }

    public List<Lecturer> searchLecturers(String query) {
        if (query == null || query.trim().isEmpty()) {
            return lecturerRepository.findAll();
        }
        return lecturerRepository.findByEmailContainingIgnoreCase(query);
    }
}
