package com.capstone.adproject.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.dto.GroupAssignmentDto;
import com.capstone.adproject.model.Admin;
import com.capstone.adproject.model.AdminCourseAssignment;
import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.StudentCourseAssignment;
import com.capstone.adproject.repositories.AdminCourseAssignmentRepository;
import com.capstone.adproject.repositories.AdminRepository;
import com.capstone.adproject.repositories.CourseRepository;
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

    @Autowired(required = false)
    private CourseRepository courseRepository;

    @Autowired(required = false)
    private AdminCourseAssignmentRepository adminCourseAssignmentRepository;

    @Autowired(required = false)
    private SuperAdminService superAdminService;

    private String getLoggedInUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getName() != null) {
            return authentication.getName();
        }
        return "";
    }

    @org.springframework.beans.factory.annotation.Value("${app.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

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
        if (existingStudentOpt.isEmpty() && student.getUsername() != null && !student.getUsername().trim().isEmpty()) {
            existingStudentOpt = studentRepository.findByUsername(student.getUsername().trim());
        }
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
    String baseUrl = (publicBaseUrl != null && !publicBaseUrl.isBlank())
            ? publicBaseUrl.replaceAll("/+$", "")
            : request.getScheme() + "://" + request.getServerName() 
                + (request.getServerPort() == 80 || request.getServerPort() == 443 ? "" : ":" + request.getServerPort()) 
                + request.getContextPath();
    String resetLink = baseUrl + "/reset_password?token=" + resetToken;
    
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

        Optional<Lecturer> existingLecturer = lecturerRepository.findFirstByEmailIgnoreCaseOrderByIdAsc(normalizedEmail);
        if (existingLecturer.isPresent()) {
            existingPassword = existingLecturer.get().getPassword();
            isTemp = existingLecturer.get().getIsTempPassword();
            resetToken = existingLecturer.get().getResetPasswordToken();
            if (lecturer.getUsername() == null || lecturer.getUsername().trim().isEmpty()) {
                lecturer.setUsername(existingLecturer.get().getUsername());
            }
            if (lecturer.getRoles() == null || lecturer.getRoles().trim().isEmpty()) {
                lecturer.setRoles(existingLecturer.get().getRoles());
            }
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
            
            sendWelcomeEmail(lecturer.getEmail(), tempPassword, "Assessor", resetToken, request);
        }
        
        if (lecturer.getUsername() == null || lecturer.getUsername().trim().isEmpty()) {
            String baseUsername = normalizedEmail.split("@")[0];
            lecturer.setUsername(generateUniqueLecturerUsername(baseUsername, activeCourse.getId()));
        } else {
            lecturer.setUsername(lecturer.getUsername().trim());
        }

        // Prevent duplicate username within the same active course
        Optional<Lecturer> duplicateInCourse = lecturerRepository.findByCourseIdAndUsernameIgnoreCase(activeCourse.getId(), lecturer.getUsername());
        if (duplicateInCourse.isPresent() && (lecturer.getId() == null || !duplicateInCourse.get().getId().equals(lecturer.getId()))) {
            throw new RuntimeException("An assessor with the name/username '" + lecturer.getUsername() + "' is already enrolled in this course.");
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
    java.util.Set<String> processedEmails = new java.util.HashSet<>();
    for (BulkUserInput user : users) {
        String trimmedEmail = user.email.trim().toLowerCase();
        if (!trimmedEmail.isEmpty()) {
            if (!processedEmails.add(trimmedEmail)) {
                continue;
            }
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
    java.util.Set<String> processedEmails = new java.util.HashSet<>();
    for (BulkUserInput user : users) {
        String trimmedEmail = user.email.trim().toLowerCase();
        if (!trimmedEmail.isEmpty()) {
            if (!processedEmails.add(trimmedEmail)) {
                continue;
            }
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

public String checkStudentEmailDuplicate(String email, Long studentIdToExclude) {
    if (email == null || email.trim().isEmpty()) {
        return "Email is required";
    }
    
    String normalizedEmail = email.replaceAll("\\s+", "").toLowerCase();
    Optional<Long> activeCourseId = getActiveCourseId();
    if (activeCourseId.isEmpty()) return null;
    ensureLegacyStudentEnrollmentsForCourse(activeCourseId.get());

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
    return generateUniqueLecturerUsername(baseUsername, getActiveCourseId().orElse(null));
}

private String generateUniqueLecturerUsername(String baseUsername, Long courseId) {
    String sanitizedBase = (baseUsername == null || baseUsername.isBlank()) ? "lecturer" : baseUsername.trim().toLowerCase();
    String candidate = sanitizedBase;
    int suffix = 1;
    while (courseId != null 
            ? lecturerRepository.findByCourseIdAndUsernameIgnoreCase(courseId, candidate).isPresent()
            : lecturerRepository.findByUsernameIgnoreCase(candidate).isPresent()) {
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
                return "This assessor is already enrolled in this course.";
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

    private List<Student> sortStudentsAlphabetically(List<Student> students) {
        if (students == null) return new java.util.ArrayList<>();
        return students.stream()
                .sorted((s1, s2) -> {
                    String name1 = (s1.getUsername() != null && !s1.getUsername().trim().isEmpty()) ? s1.getUsername() : (s1.getEmail() != null ? s1.getEmail() : "");
                    String name2 = (s2.getUsername() != null && !s2.getUsername().trim().isEmpty()) ? s2.getUsername() : (s2.getEmail() != null ? s2.getEmail() : "");
                    return name1.compareToIgnoreCase(name2);
                })
                .collect(Collectors.toList());
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

        return sortStudentsAlphabetically(new java.util.ArrayList<>(merged.values()));
    }

    @Transactional
    public void deleteStudentById(Long id) {
        Student student = studentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Student not found"));

        Optional<Long> activeCourseId = getActiveCourseId();
        if (activeCourseId.isPresent()) {
            Long courseId = activeCourseId.get();
            if (!belongsToCourse(student, courseId)) {
                throw new RuntimeException("Unauthorized: Student does not belong to the active course");
            }

            List<StudentCourseAssignment> enrollments = studentCourseAssignmentRepository.findByStudentId(id);
            boolean hasOtherEnrolledCourse = enrollments.stream()
                .anyMatch(sca -> sca.getCourse() != null && sca.getCourse().getId() != null
                        && !courseId.equals(sca.getCourse().getId()));
            boolean hasOtherPrimaryCourse = student.getCourse() != null && student.getCourse().getId() != null
                    && !courseId.equals(student.getCourse().getId());
            boolean hasOtherGroupCourse = student.getGroup() != null && student.getGroup().getCourse() != null
                    && student.getGroup().getCourse().getId() != null
                    && !courseId.equals(student.getGroup().getCourse().getId());

            if (hasOtherEnrolledCourse || hasOtherPrimaryCourse || hasOtherGroupCourse) {
                removeStudentFromGroupInCourse(student, courseId);

                studentRepository.deleteCommentsByStudentIdAndCourseId(id, courseId);
                studentRepository.deleteMarksReceivedByStudentAndCourseId(id, courseId);
                studentRepository.deleteMarksGivenByStudentAndCourseId(id, courseId);
                studentRepository.deleteLecturerAssignmentsByStudentIdAndCourseId(id, courseId);
                studentRepository.deleteAssessmentAssignmentsByStudentIdAndCourseId(id, courseId);
                studentRepository.deleteOverridesByStudentAndCourseId(id, courseId);
                studentRepository.deleteFactorHistoryByStudentAndCourseId(id, courseId);

                studentCourseAssignmentRepository.deleteByStudentIdAndCourseId(id, courseId);

                if (student.getCourse() != null && courseId.equals(student.getCourse().getId())) {
                    List<StudentCourseAssignment> remaining = studentCourseAssignmentRepository.findByStudentId(id);
                    StudentCourseAssignment nextEnrollment = remaining.stream()
                        .filter(sca -> sca.getCourse() != null && sca.getCourse().getId() != null
                                && !courseId.equals(sca.getCourse().getId()))
                        .findFirst()
                        .orElse(null);
                    if (nextEnrollment != null) {
                        student.setCourse(nextEnrollment.getCourse());
                        if (student.getGroup() == null && nextEnrollment.getGroup() != null) {
                            student.setGroup(nextEnrollment.getGroup());
                        }
                    } else if (student.getGroup() != null && student.getGroup().getCourse() != null
                            && !courseId.equals(student.getGroup().getCourse().getId())) {
                        student.setCourse(student.getGroup().getCourse());
                    }
                    studentRepository.save(student);
                }
                return;
            }
        }

        if (student.getGroup() != null) {
            removeStudentFromGroup(id);
        }

        studentRepository.deleteCommentsByStudentId(id);
        studentRepository.deleteMarksReceivedByStudent(id);
        studentRepository.deleteMarksGivenByStudent(id);
        studentRepository.deleteLecturerAssignmentsByStudentId(id);
        studentRepository.deleteAssessmentAssignmentsByStudentId(id);
        studentRepository.deleteOverridesByStudent(id);

        studentCourseAssignmentRepository.deleteByStudentId(id);
        studentRepository.delete(student);
    }

    @Transactional
    public void deleteLecturerById(Long id) {
        Lecturer lecturer = lecturerRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Lecturer not found"));

        Optional<Long> activeCourseId = getActiveCourseId();
        if (activeCourseId.isPresent() && (lecturer.getCourse() == null
                || !activeCourseId.get().equals(lecturer.getCourse().getId()))) {
            throw new RuntimeException("Unauthorized: Lecturer does not belong to the active course");
        }

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
                List<Student> courseStudents = getAllStudents();
                return courseStudents.stream()
                    .filter(student -> resolveStudentGroupForCourse(student, courseId) == null)
                    .collect(Collectors.toList());
            })
            .orElse(List.of());
    }
    
    public List<Student> getStudentsByGroup(Group group) {
        if (group == null) {
            return List.of();
        }
        Long groupCourseId = group.getCourse() != null ? group.getCourse().getId() : getActiveCourseId().orElse(null);
        List<Student> direct = studentRepository.findByGroup(group);
        if (groupCourseId == null) {
            return sortStudentsAlphabetically(direct);
        }
        List<Student> assignedViaCourse = studentCourseAssignmentRepository
            .findByCourseIdAndGroupId(groupCourseId, group.getId())
            .stream()
            .map(StudentCourseAssignment::getStudent)
            .filter(s -> s != null)
            .toList();
        return mergeStudentsById(direct, assignedViaCourse);
    }

    public Group resolveStudentGroupForActiveCourse(Student student) {
        return getActiveCourseId()
            .map(courseId -> resolveStudentGroupForCourse(student, courseId))
            .orElse(student != null ? student.getGroup() : null);
    }

    public Group resolveStudentGroupForCourse(Student student, Long courseId) {
        if (student == null || courseId == null || student.getId() == null) {
            return null;
        }
        Optional<StudentCourseAssignment> assignmentOpt = studentCourseAssignmentRepository
            .findByStudentIdAndCourseId(student.getId(), courseId);
        if (assignmentOpt.isPresent() && assignmentOpt.get().getGroup() != null) {
            Group assignedGroup = assignmentOpt.get().getGroup();
            if (assignedGroup.getCourse() != null && courseId.equals(assignedGroup.getCourse().getId())) {
                return assignedGroup;
            }
        }
        Group fallback = student.getGroup();
        if (fallback != null && fallback.getCourse() != null && courseId.equals(fallback.getCourse().getId())) {
            return fallback;
        }
        return null;
    }

    public java.util.Map<Long, Group> getStudentGroupMapForActiveCourse(List<Student> students) {
        java.util.Map<Long, Group> groupMap = new LinkedHashMap<>();
        if (students == null) {
            return groupMap;
        }
        Optional<Long> activeCourseId = getActiveCourseId();
        for (Student student : students) {
            if (student == null || student.getId() == null) {
                continue;
            }
            Group resolved = activeCourseId.isPresent()
                ? resolveStudentGroupForCourse(student, activeCourseId.get())
                : student.getGroup();
            if (resolved != null) {
                groupMap.put(student.getId(), resolved);
            }
        }
        return groupMap;
    }
    
    public List<Group> getAllGroups() {
        return getActiveCourseId()
            .map(groupRepository::findByCourseId)
            .orElse(List.of());
    }
    
    public Optional<Group> findGroupById(Long id) {
        return groupRepository.findById(id);
    }

    public boolean isLecturerInActiveCourse(Lecturer lecturer) {
        if (lecturer == null || lecturer.getId() == null) {
            return false;
        }
        return getActiveCourseId()
            .map(activeId -> isLecturerInCourse(lecturer, activeId))
            .orElse(false);
    }

    public boolean isLecturerInCourse(Lecturer lecturer, Long courseId) {
        if (lecturer == null || lecturer.getId() == null || courseId == null) {
            return false;
        }
        if (lecturer.getCourse() != null && courseId.equals(lecturer.getCourse().getId())) {
            return true;
        }
        if (adminCourseAssignmentRepository != null
                && adminCourseAssignmentRepository.existsByLecturerIdAndCourseId(lecturer.getId(), courseId)) {
            return true;
        }
        if (courseRepository != null) {
            boolean isCreator = courseRepository.findById(courseId)
                .map(c -> c.getCreatedBy() != null && lecturer.getId().equals(c.getCreatedBy().getId()))
                .orElse(false);
            if (isCreator) {
                return true;
            }
        }
        String currentUsername = getLoggedInUsername();
        if (currentUsername != null && !currentUsername.isBlank() && courseScopeService != null
                && courseScopeService.isManagedCourseId(courseId)) {
            String lecturerEmail = lecturer.getEmail();
            String lecturerUsername = lecturer.getUsername();
            if ((lecturerEmail != null && lecturerEmail.equalsIgnoreCase(currentUsername.trim())) ||
                (lecturerUsername != null && lecturerUsername.equalsIgnoreCase(currentUsername.trim()))) {
                return true;
            }
        }
        Long activeCourseId = getActiveCourseId().orElse(null);
        if (courseId.equals(activeCourseId)) {
            return getAllLecturers().stream()
                .anyMatch(l -> l.getId() != null && l.getId().equals(lecturer.getId()));
        }
        return false;
    }

    private Optional<Lecturer> findLecturerInCourse(Long lecturerId, Long courseId) {
        if (lecturerId == null || courseId == null) {
            return Optional.empty();
        }
        Optional<Lecturer> opt = lecturerRepository.findById(lecturerId);
        if (opt.isPresent() && isLecturerInCourse(opt.get(), courseId)) {
            return opt;
        }
        return getAllLecturers().stream()
            .filter(l -> l.getId() != null && l.getId().equals(lecturerId))
            .findFirst();
    }

    private void assignStudentToCourseGroup(Student student, Group targetGroup, Course course) {
        if (student == null || targetGroup == null || course == null || course.getId() == null) {
            return;
        }
        ensureStudentEnrollment(student, course);
        studentCourseAssignmentRepository.findByStudentIdAndCourseId(student.getId(), course.getId())
            .ifPresent(sca -> {
                sca.setGroup(targetGroup);
                studentCourseAssignmentRepository.save(sca);
            });

        boolean isPrimaryCourse = student.getCourse() == null
            || (student.getCourse().getId() != null && course.getId().equals(student.getCourse().getId()));
        if (isPrimaryCourse) {
            student.setGroup(targetGroup);
            if (student.getCourse() == null) {
                student.setCourse(course);
            }
        }
    }

    private void removeStudentFromGroupInCourse(Student student, Long courseId) {
        if (student == null || student.getId() == null || courseId == null) {
            return;
        }
        Group affectedGroup = resolveStudentGroupForCourse(student, courseId);
        studentCourseAssignmentRepository.findByStudentIdAndCourseId(student.getId(), courseId)
            .ifPresent(sca -> {
                if (sca.getGroup() != null) {
                    sca.setGroup(null);
                    studentCourseAssignmentRepository.save(sca);
                }
            });
        if (student.getGroup() != null && student.getGroup().getCourse() != null
                && courseId.equals(student.getGroup().getCourse().getId())) {
            student.setGroup(null);
            studentRepository.save(student);
        }
        if (affectedGroup != null) {
            affectedGroup.setGroupSize(getStudentsByGroup(affectedGroup).size());
            groupRepository.save(affectedGroup);
        }
    }

    @Transactional
    public void assignStudentsToNewGroup(GroupAssignmentDto dto) {
        Course activeCourse = resolveCurrentAdminCourse()
            .orElseThrow(() -> new RuntimeException("No active course selected."));
        Long courseId = activeCourse.getId();

        Group newGroup = new Group();
        String groupName = dto.getGroupName();
        if (groupName == null || groupName.trim().isEmpty()) {
            groupName = "Random Group " + java.util.UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        }
        newGroup.setGroupName(groupName);
        newGroup.setCourse(activeCourse);

        if (dto.getAcademicSupervisorId() != null) {
            findLecturerInCourse(dto.getAcademicSupervisorId(), courseId).ifPresent(newGroup::setAcademicSupervisor);
        }
        if (dto.getIndustrialSupervisorId() != null) {
            findLecturerInCourse(dto.getIndustrialSupervisorId(), courseId).ifPresent(newGroup::setIndustrialSupervisor);
        }

        Group savedGroup = groupRepository.save(newGroup);

        if (dto.getSelectedStudentIds() != null && !dto.getSelectedStudentIds().isEmpty()) {
            List<Student> studentsToAssign = studentRepository.findAllById(dto.getSelectedStudentIds());
            if (studentsToAssign.isEmpty() || studentsToAssign.size() < dto.getSelectedStudentIds().size()) {
                studentsToAssign = dto.getSelectedStudentIds().stream()
                    .map(this::findStudentById)
                    .flatMap(Optional::stream)
                    .toList();
            }
            studentsToAssign = studentsToAssign.stream()
                .filter(student -> belongsToCourse(student, courseId))
                .toList();

            List<Student> primaryStudentsToSave = new ArrayList<>();
            for (Student student : studentsToAssign) {
                assignStudentToCourseGroup(student, savedGroup, activeCourse);
                boolean isPrimary = student.getCourse() == null
                        || (student.getCourse().getId() != null && courseId.equals(student.getCourse().getId()));
                if (isPrimary) {
                    if (student.getUsername() == null || student.getUsername().trim().isEmpty()) {
                        student.setUsername(student.getEmail() != null ? student.getEmail().split("@")[0] : "student" + student.getId());
                    }
                    primaryStudentsToSave.add(student);
                }
            }
            if (!primaryStudentsToSave.isEmpty()) {
                studentRepository.saveAll(primaryStudentsToSave);
            }

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

        Optional<Long> activeCourseId = getActiveCourseId();
        if (activeCourseId.isPresent() && existingGroup.getCourse() != null
                && !activeCourseId.get().equals(existingGroup.getCourse().getId())) {
            throw new RuntimeException("Unauthorized: Group does not belong to the active course");
        }

        if (existingGroup.getCourse() == null) {
            resolveCurrentAdminCourse().ifPresent(existingGroup::setCourse);
        }
        Course groupCourse = existingGroup.getCourse();
        Long courseId = groupCourse != null ? groupCourse.getId() : activeCourseId.orElse(null);

        existingGroup.setGroupName(dto.getGroupName());
        
        if (dto.getAcademicSupervisorId() != null && courseId != null) {
            findLecturerInCourse(dto.getAcademicSupervisorId(), courseId)
                .ifPresent(existingGroup::setAcademicSupervisor);
        } else {
            existingGroup.setAcademicSupervisor(null);
        }
        
        if (dto.getIndustrialSupervisorId() != null && courseId != null) {
            findLecturerInCourse(dto.getIndustrialSupervisorId(), courseId)
                .ifPresent(existingGroup::setIndustrialSupervisor);
        } else {
            existingGroup.setIndustrialSupervisor(null);
        }
        
        groupRepository.save(existingGroup);

        if (dto.getSelectedStudentIds() != null && !dto.getSelectedStudentIds().isEmpty() && groupCourse != null) {
            List<Student> newStudentsToAssign = studentRepository.findAllById(dto.getSelectedStudentIds());
            if (newStudentsToAssign.isEmpty() || newStudentsToAssign.size() < dto.getSelectedStudentIds().size()) {
                newStudentsToAssign = dto.getSelectedStudentIds().stream()
                    .map(this::findStudentById)
                    .flatMap(Optional::stream)
                    .toList();
            }
            
            List<Student> studentsToAssign = newStudentsToAssign.stream()
                .filter(student -> belongsToCourse(student, groupCourse.getId()))
                .filter(student -> resolveStudentGroupForCourse(student, groupCourse.getId()) == null)
                .toList();

            List<Student> primaryToSave = new ArrayList<>();
            for (Student student : studentsToAssign) {
                assignStudentToCourseGroup(student, existingGroup, groupCourse);
                boolean isPrimary = student.getCourse() == null
                        || (student.getCourse().getId() != null && groupCourse.getId().equals(student.getCourse().getId()));
                if (isPrimary) {
                    primaryToSave.add(student);
                }
            }
            if (!primaryToSave.isEmpty()) {
                studentRepository.saveAll(primaryToSave);
            }
        }
        
        existingGroup.setGroupSize(getStudentsByGroup(existingGroup).size());
        groupRepository.save(existingGroup);
    }
    
    @Transactional
    public void removeStudentFromGroup(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found with ID: " + studentId));

        Optional<Long> activeCourseId = getActiveCourseId();
        if (activeCourseId.isPresent()) {
            removeStudentFromGroupInCourse(student, activeCourseId.get());
            return;
        }
        
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

        Optional<Long> activeCourseId = getActiveCourseId();
        if (activeCourseId.isPresent() && groupToDelete.getCourse() != null
                && !activeCourseId.get().equals(groupToDelete.getCourse().getId())) {
            throw new RuntimeException("Unauthorized: Group does not belong to the active course");
        }

        if (groupToDelete.getCourse() != null && groupToDelete.getCourse().getId() != null) {
            studentCourseAssignmentRepository.unlinkGroupFromCourseAssignments(
                groupToDelete.getCourse().getId(), groupId);
        }
        
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
                return mergeStudentsById(
                    directCourseStudents,
                    mergeStudentsById(
                        enrolledStudents,
                        mergeStudentsById(groupCourseStudents, groupCourseStudentsNative)
                    )
                );
            })
            .orElse(List.of());
    }

    public List<Lecturer> getAllLecturers() {
        Optional<Long> activeCourseIdOpt = getActiveCourseId();
        if (activeCourseIdOpt.isEmpty()) {
            return List.of();
        }
        Long courseId = activeCourseIdOpt.get();
        Set<Lecturer> result = new LinkedHashSet<>();
        result.addAll(lecturerRepository.findByCourseId(courseId));
        if (adminCourseAssignmentRepository != null) {
            List<AdminCourseAssignment> assignments = adminCourseAssignmentRepository.findByCourseId(courseId);
            for (AdminCourseAssignment aca : assignments) {
                if (aca.getLecturer() != null) {
                    result.add(aca.getLecturer());
                }
            }
        }
        if (courseRepository != null) {
            courseRepository.findById(courseId).ifPresent(c -> {
                if (c.getCreatedBy() != null) {
                    result.add(c.getCreatedBy());
                }
            });
        }
        String currentUsername = getLoggedInUsername();
        if (currentUsername != null && !currentUsername.isBlank() && superAdminService != null) {
            superAdminService.resolveLecturerByIdentity(currentUsername).ifPresent(result::add);
        }
        return new ArrayList<>(result);
    }


    public Optional<Student> findStudentById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        Optional<Student> opt = studentRepository.findById(id);
        if (opt.isPresent()) {
            return opt;
        }
        Optional<Student> nativeOpt = studentRepository.findByIdNative(id);
        if (nativeOpt.isPresent()) {
            return nativeOpt;
        }
        return getAllStudents().stream()
            .filter(s -> s.getId() != null && s.getId().equals(id))
            .findFirst();
    }

    public Optional<Lecturer> findLecturerById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        Optional<Lecturer> opt = lecturerRepository.findById(id);
        if (opt.isPresent()) {
            return opt;
        }
        return getAllLecturers().stream()
            .filter(l -> l.getId() != null && l.getId().equals(id))
            .findFirst();
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
        if (belongsToCourse(student, activeCourseId.get())) {
            return true;
        }
        return getAllStudents().stream()
            .anyMatch(s -> s.getId() != null && s.getId().equals(student.getId()));
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
            Optional<Long> activeCourseId = getActiveCourseId();
            Optional<Lecturer> existingLecturerOpt = activeCourseId.isPresent()
                ? lecturerRepository.findByCourseIdAndEmailIgnoreCase(activeCourseId.get(), normalizedEmail)
                : CourseScopeService.resolveLecturersByIdentity(lecturerRepository, normalizedEmail).stream().findFirst();
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
        List<Lecturer> all = getAllLecturers();
        if (query == null || query.trim().isEmpty()) {
            return all;
        }
        String q = query.trim().toLowerCase();
        return all.stream()
            .filter(l -> (l.getEmail() != null && l.getEmail().toLowerCase().contains(q)) ||
                         (l.getUsername() != null && l.getUsername().toLowerCase().contains(q)))
            .collect(Collectors.toList());
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
