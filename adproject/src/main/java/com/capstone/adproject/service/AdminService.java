package com.capstone.adproject.service;

import java.util.LinkedHashMap;
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
import com.capstone.adproject.model.StudentCourseAssignment;
import com.capstone.adproject.repositories.AdminRepository;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.StudentCourseAssignmentRepository;
import com.capstone.adproject.repositories.StudentRepository;
import com.capstone.adproject.util.PasswordGenerator;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class AdminService {

    private static class BulkUserInput {
        private final String email;
        private final String username;

        private BulkUserInput(String email, String username) {
            this.email = email;
            this.username = username;
        }
    }

    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;
    private final LecturerGroupAssignmentRepository assignmentRepository;
    private final GroupRepository groupRepository;
    private final StudentCourseAssignmentRepository studentCourseAssignmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminRepository adminRepository;
    private final CourseScopeService courseScopeService;

    @Autowired
    public AdminService(
            StudentRepository studentRepository, 
            LecturerRepository lecturerRepository, 
            GroupRepository groupRepository, 
            StudentCourseAssignmentRepository studentCourseAssignmentRepository,
            PasswordEncoder passwordEncoder,
            LecturerGroupAssignmentRepository assignmentRepository,
            AdminRepository adminRepository,
            CourseScopeService courseScopeService) {
        this.studentRepository = studentRepository;
        this.lecturerRepository = lecturerRepository;
        this.groupRepository = groupRepository;
        this.studentCourseAssignmentRepository = studentCourseAssignmentRepository;
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

        String normalizedEmail = student.getEmail().trim().toLowerCase();
        student.setEmail(normalizedEmail);

    if (student.getId() != null) {
        Student existingStudent = studentRepository.findById(student.getId())
            .orElseThrow(() -> new RuntimeException("Student not found"));
        
        String submittedUsername = student.getUsername();
        student.setPassword(existingStudent.getPassword());
        student.setIsTempPassword(existingStudent.getIsTempPassword());
        student.setResetPasswordToken(existingStudent.getResetPasswordToken());
        student.setCourse(existingStudent.getCourse());
        
        if (!existingStudent.getEmail().equals(student.getEmail())) {
            student.setEmail(student.getEmail());
        }

        if (submittedUsername == null || submittedUsername.trim().isEmpty()) {
            if (existingStudent.getUsername() == null || existingStudent.getUsername().trim().isEmpty()) {
                String baseUsername = normalizedEmail.split("@")[0];
                student.setUsername(generateUniqueStudentUsername(baseUsername));
            } else {
                student.setUsername(existingStudent.getUsername());
            }
        } else {
            student.setUsername(submittedUsername.trim());
        }

        resolveCurrentAdminCourse().ifPresent(course -> ensureStudentEnrollment(existingStudent, course));
        
    } else {
        Course activeCourse = resolveCurrentAdminCourse()
            .orElseThrow(() -> new RuntimeException("No active course selected for student creation"));

        Optional<Student> existingStudentOpt = studentRepository.findFirstByEmailIgnoreCaseOrderByIdAsc(normalizedEmail);
        if (existingStudentOpt.isPresent()) {
            Student existingStudent = existingStudentOpt.get();
            ensureStudentEnrollment(existingStudent, activeCourse);

            // Avoid touching legacy student rows during re-enrollment to prevent validation
            // failures on old nullable username data.
            return;
        }

        student.setCourse(activeCourse);

        // Check if user already exists in other tables to sync password
        String existingPassword = null;
        Boolean isTemp = true;
        String resetToken = UUID.randomUUID().toString();
        
        Optional<Admin> existingAdmin = adminRepository.findByEmail(normalizedEmail);
        if (existingAdmin.isPresent()) {
            existingPassword = existingAdmin.get().getPassword();
            isTemp = false;
            resetToken = existingAdmin.get().getResetPasswordToken();
        }
        
        Optional<Lecturer> existingLecturer = lecturerRepository.findFirstByEmailIgnoreCaseOrderByIdAsc(normalizedEmail);
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
            String baseUsername = normalizedEmail.split("@")[0];
            student.setUsername(generateUniqueStudentUsername(baseUsername));
        }
        Student savedStudent = studentRepository.save(student);
        ensureStudentEnrollment(savedStudent, activeCourse);
        return; 
    }
    
    studentRepository.save(student);
}

private void ensureStudentEnrollment(Student student, Course course) {
    if (student == null || student.getId() == null || course == null || course.getId() == null) {
        return;
    }

    boolean enrolled = studentCourseAssignmentRepository.existsByStudentIdAndCourseId(student.getId(), course.getId());
    if (!enrolled) {
        StudentCourseAssignment assignment = new StudentCourseAssignment();
        assignment.setStudent(student);
        assignment.setCourse(course);
        studentCourseAssignmentRepository.save(assignment);
    }
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

    String normalizedEmail = lecturer.getEmail().trim().toLowerCase();
    lecturer.setEmail(normalizedEmail);

    if (lecturer.getId() != null) {
        Lecturer existingLecturer = lecturerRepository.findById(lecturer.getId())
            .orElseThrow(() -> new RuntimeException("Lecturer not found"));
        
        String submittedUsername = lecturer.getUsername();
        lecturer.setPassword(existingLecturer.getPassword());
        lecturer.setIsTempPassword(existingLecturer.getIsTempPassword());
        lecturer.setResetPasswordToken(existingLecturer.getResetPasswordToken());
        lecturer.setRoles(existingLecturer.getRoles()); // Preserve existing roles
        lecturer.setCourse(existingLecturer.getCourse());

        if (submittedUsername == null || submittedUsername.trim().isEmpty()) {
            if (existingLecturer.getUsername() == null || existingLecturer.getUsername().trim().isEmpty()) {
                String baseUsername = normalizedEmail.split("@")[0];
                lecturer.setUsername(generateUniqueLecturerUsername(baseUsername));
            } else {
                lecturer.setUsername(existingLecturer.getUsername());
            }
        } else {
            lecturer.setUsername(submittedUsername.trim());
        }
        
    } else {
        Course activeCourse = resolveCurrentAdminCourse()
            .orElseThrow(() -> new RuntimeException("No active course selected for lecturer creation"));
        lecturer.setCourse(activeCourse);

        // Check if user already exists in other tables to sync password
        String existingPassword = null;
        Boolean isTemp = true;
        String resetToken = UUID.randomUUID().toString();
        
        Optional<Admin> existingAdmin = adminRepository.findByEmail(normalizedEmail);
        if (existingAdmin.isPresent()) {
            existingPassword = existingAdmin.get().getPassword();
            isTemp = false;
            resetToken = existingAdmin.get().getResetPasswordToken();
        }

        if (existingPassword != null) {
            lecturer.setPassword(existingPassword);
            lecturer.setIsTempPassword(isTemp);
            lecturer.setResetPasswordToken(resetToken);
            if (lecturer.getRoles() == null || lecturer.getRoles().trim().isEmpty()) {
                lecturer.setRoles("ROLE_LECTURER");
            }
        } else {
            String tempPassword = PasswordGenerator.generateRandomPassword();
            lecturer.setPassword(passwordEncoder.encode(tempPassword));
            lecturer.setIsTempPassword(true);
            lecturer.setResetPasswordToken(resetToken);
            lecturer.setRoles("ROLE_LECTURER"); // Default role for new lecturer
            
            sendWelcomeEmail(lecturer.getEmail(), tempPassword, "Lecturer", resetToken, request);
        }
        
        if (lecturer.getUsername() == null || lecturer.getUsername().trim().isEmpty()) {
            String baseUsername = normalizedEmail.split("@")[0];
            lecturer.setUsername(generateUniqueLecturerUsername(baseUsername));
        }
        lecturerRepository.save(lecturer);
        return;
    }
    
    lecturerRepository.save(lecturer);
}


@Transactional
public int bulkAddStudents(List<String> emails, List<String> usernames, HttpServletRequest request) {
    List<BulkUserInput> users = buildBulkInputs(emails, usernames);
    int count = 0;
    for (BulkUserInput user : users) {
        String trimmedEmail = user.email.trim().toLowerCase();
        if (!trimmedEmail.isEmpty()) {
            if (checkStudentEmailDuplicate(trimmedEmail, null) == null) {
                Student student = new Student();
                student.setEmail(trimmedEmail);
                if (user.username != null && !user.username.trim().isEmpty()) {
                    student.setUsername(user.username.trim());
                }
                saveStudent(student, request);
                count++;
            }
        }
    }
    return count;
}

@Transactional
public int bulkAddLecturers(List<String> emails, List<String> usernames, HttpServletRequest request) {
    List<BulkUserInput> users = buildBulkInputs(emails, usernames);
    int count = 0;
    for (BulkUserInput user : users) {
        String trimmedEmail = user.email.trim().toLowerCase();
        if (!trimmedEmail.isEmpty()) {
            if (checkLecturerEmailDuplicate(trimmedEmail, null) == null) {
                Lecturer lecturer = new Lecturer();
                lecturer.setEmail(trimmedEmail);
                if (user.username != null && !user.username.trim().isEmpty()) {
                    lecturer.setUsername(user.username.trim());
                }
                saveLecturer(lecturer, request);
                count++;
            }
        }
    }
    return count;
}

private List<BulkUserInput> buildBulkInputs(List<String> emails, List<String> usernames) {
    List<String> safeEmails = emails == null ? java.util.Collections.emptyList() : emails;
    List<String> safeUsernames = usernames == null ? java.util.Collections.emptyList() : usernames;
    int emailCount = safeEmails.size();
    int usernameCount = safeUsernames.size();
    int max = Math.max(emailCount, usernameCount);

    List<BulkUserInput> rows = new java.util.ArrayList<>();
    for (int i = 0; i < max; i++) {
        String email = i < emailCount && safeEmails.get(i) != null ? safeEmails.get(i) : "";
        String username = i < usernameCount && safeUsernames.get(i) != null ? safeUsernames.get(i) : "";
        rows.add(new BulkUserInput(email, username));
    }
    return rows;
}

public String checkStudentEmailDuplicate(String email, Long studentIdToExclude) {    if (email == null || email.trim().isEmpty()) {
        return "Email is required";
    }
    
    String normalizedEmail = email.replaceAll("\\s+", "").toLowerCase();
    Optional<Long> activeCourseId = getActiveCourseId();
    if (activeCourseId.isEmpty()) return null;

    if (studentIdToExclude != null) {
        Optional<Student> excluded = studentRepository.findById(studentIdToExclude);
        if (excluded.isPresent() && excluded.get().getEmail() != null
            && excluded.get().getEmail().trim().equalsIgnoreCase(normalizedEmail)) {
            return null;
        }
    }

    if (studentCourseAssignmentRepository.existsByCourseIdAndEmailIgnoreCase(activeCourseId.get(), normalizedEmail)) {
        return "This student is already enrolled in this course.";
    }
    return null;
}

private String generateUniqueStudentUsername(String baseUsername) {
    String sanitizedBase = (baseUsername == null || baseUsername.isBlank()) ? "student" : baseUsername.trim().toLowerCase();
    String candidate = sanitizedBase;
    int suffix = 1;
    while (studentRepository.findByUsername(candidate).isPresent()) {
        candidate = sanitizedBase + suffix;
        suffix++;
    }
    return candidate;
}

private String generateUniqueLecturerUsername(String baseUsername) {
    String sanitizedBase = (baseUsername == null || baseUsername.isBlank()) ? "lecturer" : baseUsername.trim().toLowerCase();
    String candidate = sanitizedBase;
    int suffix = 1;
    while (lecturerRepository.findByUsernameIgnoreCase(candidate).isPresent()) {
        candidate = sanitizedBase + suffix;
        suffix++;
    }
    return candidate;
}

private Optional<Long> getActiveCourseId() {
    return Optional.ofNullable(courseScopeService.getActiveCourseIdForCurrentUser());
}

private void ensureLegacyStudentEnrollmentsForCourse(Long courseId) {
    if (courseId == null) {
        return;
    }

    List<Student> legacyStudents = studentRepository.findByCourseId(courseId);
    for (Student legacyStudent : legacyStudents) {
        if (legacyStudent.getId() == null || legacyStudent.getCourse() == null) {
            continue;
        }
        ensureStudentEnrollment(legacyStudent, legacyStudent.getCourse());
    }

    List<Student> groupScopedStudents = studentRepository.findByGroupCourseIdNative(courseId);
    for (Student groupScopedStudent : groupScopedStudents) {
        if (groupScopedStudent == null || groupScopedStudent.getId() == null
                || groupScopedStudent.getGroup() == null
                || groupScopedStudent.getGroup().getCourse() == null) {
            continue;
        }

        Course groupCourse = groupScopedStudent.getGroup().getCourse();
        ensureStudentEnrollment(groupScopedStudent, groupCourse);

        if (groupScopedStudent.getCourse() == null || groupScopedStudent.getCourse().getId() == null) {
            groupScopedStudent.setCourse(groupCourse);
            studentRepository.save(groupScopedStudent);
        }
    }
}

public String checkLecturerEmailDuplicate(String email, Long lecturerIdToExclude) {
    if (email == null || email.trim().isEmpty()) {
        return "Email is required";
    }
    
    String normalizedEmail = email.replaceAll("\\s+", "").toLowerCase();
    Optional<Long> activeCourseId = getActiveCourseId();
    if (activeCourseId.isEmpty()) return null;
    ensureLegacyStudentEnrollmentsForCourse(activeCourseId.get());
    
    List<Lecturer> lecturersInCourse = lecturerRepository.findByCourseId(activeCourseId.get());
    for (Lecturer lecturer : lecturersInCourse) {
        if (lecturerIdToExclude != null && lecturer.getId().equals(lecturerIdToExclude)) continue;
        if (lecturer.getEmail() != null) {
            String existingNormalized = lecturer.getEmail().replaceAll("\\s+", "").toLowerCase();
            if (existingNormalized.equals(normalizedEmail)) {
                return "This lecturer is already enrolled in this course.";
            }
        }
    }
    return null;
}

    private boolean belongsToCourse(Student student, Long courseId) {
        if (student == null || courseId == null || student.getId() == null) {
            return false;
        }

        if (student.getCourse() != null && student.getCourse().getId() != null && courseId.equals(student.getCourse().getId())) {
            return true;
        }

        if (student.getGroup() != null && student.getGroup().getCourse() != null && student.getGroup().getCourse().getId() != null
                && courseId.equals(student.getGroup().getCourse().getId())) {
            return true;
        }

        return studentCourseAssignmentRepository.existsByStudentIdAndCourseId(student.getId(), courseId);
    }

    private List<Student> mergeStudentsById(List<Student> primary, List<Student> secondary) {
        LinkedHashMap<Long, Student> merged = new LinkedHashMap<>();

        if (primary != null) {
            for (Student student : primary) {
                if (student != null && student.getId() != null) {
                    merged.putIfAbsent(student.getId(), student);
                }
            }
        }

        if (secondary != null) {
            for (Student student : secondary) {
                if (student != null && student.getId() != null) {
                    merged.putIfAbsent(student.getId(), student);
                }
            }
        }

        return new java.util.ArrayList<>(merged.values());
    }

    @Transactional
    public void deleteStudentById(Long id) {
        Student student = studentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Student not found"));
        
        if (student.getGroup() != null) {
            removeStudentFromGroup(id);
        }

        studentRepository.deleteCommentsByStudentId(id);
        studentRepository.deleteMarksReceivedByStudent(id);
        studentRepository.deleteMarksGivenByStudent(id);
        studentRepository.deleteLecturerAssignmentsByStudentId(id);
        studentRepository.deleteAssessmentAssignmentsByStudentId(id);
        studentRepository.deleteOverridesByStudent(id);

        Optional<Long> activeCourseId = getActiveCourseId();
        if (activeCourseId.isPresent()
                && studentCourseAssignmentRepository.existsByStudentIdAndCourseId(id, activeCourseId.get())
                && studentCourseAssignmentRepository.countByStudentId(id) > 1) {

            if (student.getGroup() != null
                    && student.getGroup().getCourse() != null
                    && activeCourseId.get().equals(student.getGroup().getCourse().getId())) {
                removeStudentFromGroup(id);
            }

            studentCourseAssignmentRepository.deleteByStudentIdAndCourseId(id, activeCourseId.get());

            if (student.getCourse() != null && activeCourseId.get().equals(student.getCourse().getId())) {
                studentCourseAssignmentRepository.findByStudentId(id).stream()
                    .map(StudentCourseAssignment::getCourse)
                    .filter(c -> c != null && c.getId() != null)
                    .findFirst()
                    .ifPresent(student::setCourse);
                studentRepository.save(student);
            }
            return;
        }

        studentCourseAssignmentRepository.deleteByStudentId(id);
        studentRepository.delete(student);
    }

    @Transactional
    public void deleteLecturerById(Long id) {
        Lecturer lecturer = lecturerRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Lecturer not found"));

        lecturerRepository.unlinkFromGroups(id);

        lecturerRepository.deleteGroupAssignments(id);

        lecturerRepository.deleteStudentAssignments(id);

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
            .map(courseId -> {
                ensureLegacyStudentEnrollmentsForCourse(courseId);
                List<Student> directCourseStudents = studentRepository.findByCourseIdAndGroupIsNull(courseId);
                List<Student> enrolledStudents = studentCourseAssignmentRepository.findStudentsWithoutGroupByCourseId(courseId);
                List<Student> groupCourseStudents = studentRepository.findByGroupCourseIdWithGroupFetched(courseId).stream()
                    .filter(student -> student.getGroup() == null)
                    .collect(Collectors.toList());
                return mergeStudentsById(directCourseStudents, mergeStudentsById(enrolledStudents, groupCourseStudents));
            })
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
            .map(courseId -> {
                ensureLegacyStudentEnrollmentsForCourse(courseId);
                List<Student> directCourseStudents = studentRepository.findByCourseId(courseId);
                List<Student> enrolledStudents = studentCourseAssignmentRepository.findStudentsByCourseId(courseId);
                List<Student> groupCourseStudents = studentRepository.findByGroupCourseIdWithGroupFetched(courseId);
                List<Student> groupCourseStudentsNative = studentRepository.findByGroupCourseIdNative(courseId);
                List<Student> merged = mergeStudentsById(
                    directCourseStudents,
                    mergeStudentsById(
                        enrolledStudents,
                        mergeStudentsById(groupCourseStudents, groupCourseStudentsNative)
                    )
                );

                List<Long> visibleGroupIds = merged.stream()
                    .map(Student::getGroup)
                    .filter(g -> g != null && g.getId() != null)
                    .map(Group::getId)
                    .distinct()
                    .toList();

                if (!visibleGroupIds.isEmpty()) {
                    List<Student> groupPeers = studentRepository.findByGroupIdIn(visibleGroupIds);
                    merged = mergeStudentsById(merged, groupPeers);
                }

                return merged;
            })
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
            .map(courseId -> {
                ensureLegacyStudentEnrollmentsForCourse(courseId);
                return (long) getStudentsWithoutGroup().size();
            })
            .orElse(0L); 
    }

    public boolean isStudentInActiveCourse(Student student) {
        if (student == null || student.getId() == null) {
            return false;
        }

        Optional<Long> activeCourseId = getActiveCourseId();
        if (activeCourseId.isEmpty()) {
            return false;
        }

        ensureLegacyStudentEnrollmentsForCourse(activeCourseId.get());
        return belongsToCourse(student, activeCourseId.get());
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
    
    @Transactional
    public void ensureAdminUserExists(Lecturer lecturer) {
        if (lecturer == null || lecturer.getEmail() == null || lecturer.getEmail().isBlank()) {
            return;
        }

        String normalizedEmail = lecturer.getEmail().trim().toLowerCase();

        // Check if an admin record already exists. If not, create one.
        adminRepository.findByEmail(normalizedEmail).orElseGet(() -> {
            Admin newAdmin = new Admin();
            newAdmin.setEmail(normalizedEmail);
            newAdmin.setUsername(lecturer.getUsername());
            newAdmin.setPassword(lecturer.getPassword()); // Sync password
            newAdmin.setResetPasswordToken(lecturer.getResetPasswordToken());
            return adminRepository.save(newAdmin);
        });
    }
}
