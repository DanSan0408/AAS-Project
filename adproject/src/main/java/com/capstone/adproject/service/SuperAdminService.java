package com.capstone.adproject.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.model.Admin;
import com.capstone.adproject.model.AdminCourseAssignment;
import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.SuperAdmin;
import com.capstone.adproject.repositories.AdminCourseAssignmentRepository;
import com.capstone.adproject.repositories.AdminRepository;
import com.capstone.adproject.repositories.CourseRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.SuperAdminRepository;
import com.capstone.adproject.util.PasswordGenerator;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class SuperAdminService {

    private final CourseRepository courseRepository;
    private final LecturerRepository lecturerRepository;
    private final AdminRepository adminRepository;
    private final SuperAdminRepository superAdminRepository;
    private final AdminCourseAssignmentRepository adminCourseAssignmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final CourseStatisticsService courseStatisticsService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    @PersistenceContext
    private EntityManager entityManager;

    public SuperAdminService(CourseRepository courseRepository, LecturerRepository lecturerRepository,
            AdminRepository adminRepository,
            SuperAdminRepository superAdminRepository,
            AdminCourseAssignmentRepository adminCourseAssignmentRepository,
            PasswordEncoder passwordEncoder, EmailService emailService, CourseStatisticsService courseStatisticsService,
            JdbcTemplate jdbcTemplate) {
        this.courseRepository = courseRepository;
        this.lecturerRepository = lecturerRepository;
        this.adminRepository = adminRepository;
        this.superAdminRepository = superAdminRepository;
        this.adminCourseAssignmentRepository = adminCourseAssignmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.courseStatisticsService = courseStatisticsService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Course> getAllCourses() {
        return courseRepository.findAll();
    }

    public List<Lecturer> getAllAdminLecturers() {
        return lecturerRepository.findByRolesContaining("ROLE_ADMIN")
                .stream()
                .filter(l -> l.getEmail() != null && !l.getEmail().isBlank())
                .collect(Collectors.toList());
    }

    @Transactional
    public void ensureSuperAdminAssignable(String superAdminEmail) {
        if (superAdminEmail == null || superAdminEmail.isBlank()) {
            return;
        }

        List<Lecturer> existingLecturers = CourseScopeService.resolveLecturersByIdentity(lecturerRepository, superAdminEmail);
        if (!existingLecturers.isEmpty()) {
            Lecturer lecturer = existingLecturers.get(0);
            String roles = lecturer.getRoles() == null ? "" : lecturer.getRoles();
            boolean changed = false;

            if (!roles.contains("ROLE_ADMIN")) {
                roles = roles.isBlank() ? "ROLE_ADMIN" : roles + ",ROLE_ADMIN";
                changed = true;
            }
            if (!roles.contains("ROLE_LECTURER")) {
                roles = roles.isBlank() ? "ROLE_LECTURER" : roles + ",ROLE_LECTURER";
                changed = true;
            }

            if (changed) {
                lecturer.setRoles(roles);
                lecturerRepository.save(lecturer);
            }
            return;
        }

        SuperAdmin superAdmin = superAdminRepository.findByEmail(superAdminEmail)
                .orElseThrow(() -> new IllegalArgumentException("Super admin user not found."));

        Lecturer lecturer = new Lecturer();
        lecturer.setEmail(superAdmin.getEmail());
        lecturer.setPassword(superAdmin.getPassword());
        lecturer.setRoles("ROLE_ADMIN,ROLE_LECTURER");
        lecturer.setIsTempPassword(false);
        lecturer.setResetPasswordToken(superAdmin.getResetPasswordToken());

        String candidateUsername = superAdmin.getUsername();
        if (candidateUsername != null && !candidateUsername.isBlank()
                && lecturerRepository.findByUsername(candidateUsername).isEmpty()) {
            lecturer.setUsername(candidateUsername);
        } else {
            lecturer.setUsername(null);
        }

        lecturerRepository.save(lecturer);
    }

    public Optional<Course> getCourseById(Long id) {
        return courseRepository.findById(id);
    }

    public Optional<Course> getCourseByCode(String courseCode) {
        if (courseCode == null || courseCode.isBlank()) {
            return Optional.empty();
        }
        return courseRepository.findByCourseCodeIgnoreCase(courseCode.trim());
    }

    public Course saveCourse(Course course) {
        if (course == null) {
            throw new IllegalArgumentException("Course is required.");
        }

        if (course.getCourseName() != null) {
            course.setCourseName(course.getCourseName().trim());
        }
        if (course.getCourseCode() != null) {
            course.setCourseCode(course.getCourseCode().trim());
        }
        if (course.getDescription() != null) {
            course.setDescription(course.getDescription().trim());
        }

        // Validate required fields
        if (course.getCourseName() == null || course.getCourseName().isBlank()) {
            throw new IllegalArgumentException("Course name is required.");
        }
        if (course.getCourseCode() == null || course.getCourseCode().isBlank()) {
            throw new IllegalArgumentException("Course code is required.");
        }

        // Check if course code already exists
        Optional<Course> existing = courseRepository.findByCourseCodeIgnoreCase(course.getCourseCode());
        if (existing.isPresent() && (course.getId() == null || !existing.get().getId().equals(course.getId()))) {
            throw new IllegalArgumentException("The course exists, and the course ID has already been used. Please assign a new course ID.");
        }

        return courseRepository.save(course);
    }

        @Transactional
        public void deleteCourse(Long id) {
        entityManager.flush();
        entityManager.clear();

        // Delete rubric templates and course-scoped overrides/weightages first (have FK to courses)
        entityManager.createNativeQuery("DELETE FROM `rubric_templates` WHERE course_id = :courseId")
            .setParameter("courseId", id)
            .executeUpdate();

        entityManager.createNativeQuery("DELETE FROM `factor_weightages` WHERE course_id = :courseId")
            .setParameter("courseId", id)
            .executeUpdate();

        entityManager.createNativeQuery("DELETE FROM `student_result_overrides` WHERE course_id = :courseId")
            .setParameter("courseId", id)
            .executeUpdate();

        entityManager.createNativeQuery("DELETE FROM `factor_override_history` WHERE course_id = :courseId")
            .setParameter("courseId", id)
            .executeUpdate();

        // Detach all known references before deleting the course row.
        entityManager.createNativeQuery("DELETE FROM `admin_course_assignment` WHERE course_id = :courseId")
            .setParameter("courseId", id)
            .executeUpdate();

        entityManager.createNativeQuery("DELETE FROM `student_course_assignment` WHERE course_id = :courseId")
            .setParameter("courseId", id)
            .executeUpdate();

        entityManager.createNativeQuery("UPDATE `student` SET course_id = NULL WHERE course_id = :courseId")
            .setParameter("courseId", id)
            .executeUpdate();

        entityManager.createNativeQuery("UPDATE `lecturer` SET course_id = NULL WHERE course_id = :courseId")
            .setParameter("courseId", id)
            .executeUpdate();

        entityManager.createNativeQuery("UPDATE `project_group` SET course_id = NULL WHERE course_id = :courseId")
            .setParameter("courseId", id)
            .executeUpdate();

        entityManager.createNativeQuery("UPDATE `assessment` SET course_id = NULL WHERE course_id = :courseId")
            .setParameter("courseId", id)
            .executeUpdate();

        entityManager.createNativeQuery("DELETE FROM `courses` WHERE id = :courseId")
            .setParameter("courseId", id)
            .executeUpdate();
    }

    /**
     * Get the course assigned to the user with the given email
     * @param email User email
     * @return Course if found, Optional.empty() otherwise
     */
    public Optional<Course> getUserCourse(String email) {
        List<Course> managedCourses = getManagedCoursesForAdminEmail(email);
        return managedCourses.isEmpty() ? Optional.empty() : Optional.of(managedCourses.get(0));
    }

    public List<Course> getManagedCoursesForAdminEmail(String email) {
        List<Lecturer> lecturers = CourseScopeService.resolveLecturersByIdentity(lecturerRepository, email);
        if (lecturers.isEmpty()) {
            return List.of();
        }

        Set<Course> result = new java.util.LinkedHashSet<>();
        for (Lecturer lecturer : lecturers) {
            List<Course> assigned = adminCourseAssignmentRepository.findByLecturerId(lecturer.getId())
                    .stream()
                    .map(AdminCourseAssignment::getCourse)
                    .filter(c -> c != null)
                    .collect(Collectors.toList());

            if (assigned.isEmpty() && lecturer.getCourse() != null) {
                result.add(lecturer.getCourse());
            } else {
                result.addAll(assigned);
            }
        }
        return new ArrayList<>(result);
    }

    public Map<Long, List<Course>> getAdminManagedCoursesMap(List<Lecturer> admins) {
        Map<Long, List<Course>> managedCoursesMap = new LinkedHashMap<>();
        for (Lecturer admin : admins) {
            List<Course> assigned = adminCourseAssignmentRepository.findByLecturerId(admin.getId())
                    .stream()
                    .map(AdminCourseAssignment::getCourse)
                    .filter(c -> c != null)
                    .collect(Collectors.toList());

            if (assigned.isEmpty() && admin.getCourse() != null) {
                assigned = List.of(admin.getCourse());
            }

            managedCoursesMap.put(admin.getId(), assigned);
        }
        return managedCoursesMap;
    }

    @Transactional
    public void deleteAdminById(Long adminId, String currentSuperAdminIdentity) {
        Lecturer admin = lecturerRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found."));

        String roles = admin.getRoles() == null ? "" : admin.getRoles();
        if (!roles.contains("ROLE_ADMIN")) {
            throw new IllegalArgumentException("Selected user is not an admin.");
        }

        if (roles.contains("ROLE_SUPER_ADMIN") || superAdminRepository.findByEmail(admin.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Super admin accounts cannot be deleted from this page.");
        }

        if (currentSuperAdminIdentity != null && !currentSuperAdminIdentity.isBlank()) {
            String identity = currentSuperAdminIdentity.trim();
            boolean deletingCurrentUser = identity.equalsIgnoreCase(admin.getEmail())
                    || (admin.getUsername() != null && identity.equalsIgnoreCase(admin.getUsername()));
            if (deletingCurrentUser) {
                throw new IllegalArgumentException("You cannot delete your own account.");
            }
        }

        adminCourseAssignmentRepository.deleteByLecturerId(adminId);
        lecturerRepository.unlinkFromGroups(adminId);
        lecturerRepository.deleteGroupAssignments(adminId);
        lecturerRepository.deleteMarksGiven(adminId);
        lecturerRepository.deleteCommentsByLecturer(adminId);
        lecturerRepository.delete(admin);
    }

    /**
     * Get statistics for a specific course
     * @param courseId The course ID
     * @return Map containing course statistics
     */
    public Map<String, Object> getCourseSummary(Long courseId) {
        Optional<Course> courseOpt = courseRepository.findById(courseId);
        if (courseOpt.isPresent()) {
            return courseStatisticsService.getCourseStatistics(courseOpt.get());
        }
        return Map.of();
    }

    private String buildResetPasswordLink(String token) {
        String normalizedBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return normalizedBaseUrl + "/reset_password?token=" + token;
    }

    @Transactional
    public void assignAdminToCourse(Long lecturerId, Long courseId) {
        Lecturer admin = lecturerRepository.findById(lecturerId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found."));

        if (admin.getRoles() == null || !admin.getRoles().contains("ROLE_ADMIN")) {
            throw new IllegalArgumentException("Selected user is not an admin.");
        }

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found."));

        saveAdminAssignment(admin, course);

        // Keep legacy single-course field populated for backward compatibility.
        if (admin.getCourse() == null) {
            admin.setCourse(course);
            lecturerRepository.save(admin);
        }
    }

    @Transactional
    public boolean inviteAdmin(String email, String name, Long courseId, HttpServletRequest request) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be empty.");
        }

        // Step 1: Create or find the user in the `admin` table. This is the new source of truth.
        boolean[] emailSent = {false};
        Admin admin = adminRepository.findByEmail(email).orElseGet(() -> {
            Admin newAdmin = new Admin();
            newAdmin.setEmail(email);
            newAdmin.setUsername(name != null && !name.isBlank() ? name : email.split("@")[0]);

            String tempPassword = PasswordGenerator.generateRandomPassword();
            newAdmin.setPassword(passwordEncoder.encode(tempPassword));
            newAdmin.setResetPasswordToken(java.util.UUID.randomUUID().toString());
            
            String resetLink = getBaseUrl(request) + "/reset_password?token=" + newAdmin.getResetPasswordToken();
            emailSent[0] = emailService.sendWelcomeEmailWithPassword(email, tempPassword, "Admin", resetLink);
            
            return adminRepository.save(newAdmin);
        });

        // Step 2: Create or find a corresponding `lecturer` record for compatibility.
        List<Lecturer> matchedLecturers = CourseScopeService.resolveLecturersByIdentity(lecturerRepository, email);
        Lecturer adminLecturer = !matchedLecturers.isEmpty() ? matchedLecturers.get(0) : null;
        if (adminLecturer == null) {
            Lecturer newLecturer = new Lecturer();
            newLecturer.setEmail(email);
            newLecturer.setUsername(admin.getUsername()); // Sync username
            newLecturer.setPassword(admin.getPassword()); // Sync password
            newLecturer.setIsTempPassword(true); // New lecturers created this way always start with a temp password
            newLecturer.setRoles("ROLE_LECTURER,ROLE_ADMIN"); // Assign both roles
            if (emailSent[0]) {
                newLecturer.setWelcomeEmailSentAt(java.time.LocalDateTime.now());
            }
            adminLecturer = lecturerRepository.save(newLecturer);
        }

        // Ensure roles are correct in the lecturer table
        if (adminLecturer.getRoles() == null || !adminLecturer.getRoles().contains("ROLE_ADMIN")) {
            adminLecturer.setRoles("ROLE_LECTURER,ROLE_ADMIN");
            lecturerRepository.save(adminLecturer);
        }

        if (courseId != null) {
            assignAdminToCourse(adminLecturer.getId(), courseId);
        }
        return emailSent[0];
    }

    private String getBaseUrl(HttpServletRequest request) {
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            return publicBaseUrl.replaceAll("/+$", "");
        }
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String contextPath = request.getContextPath();
        
        return scheme + "://" + serverName + (serverPort == 80 || serverPort == 443 ? "" : ":" + serverPort) + contextPath;
    }

    @Transactional
    public void inviteExistingAdminToCreatedCourse(String inviterIdentity, String invitedAdminEmail, Long courseId) {
        if (inviterIdentity == null || inviterIdentity.isBlank()) {
            throw new IllegalArgumentException("Invalid inviter account.");
        }
        if (invitedAdminEmail == null || invitedAdminEmail.isBlank()) {
            throw new IllegalArgumentException("Invited admin email is required.");
        }
        if (courseId == null) {
            throw new IllegalArgumentException("Course is required.");
        }

        Lecturer inviter = resolveLecturerByIdentity(inviterIdentity)
                .orElseThrow(() -> new IllegalArgumentException("Inviter admin account not found."));
        if (inviter.getRoles() == null || !inviter.getRoles().contains("ROLE_ADMIN")) {
            throw new IllegalArgumentException("Only admins can invite co-admins.");
        }

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found."));

        if (course.getCreatedBy() == null || course.getCreatedBy().getId() == null) {
            // Legacy fallback: if creator metadata is missing, allow a managing admin to claim ownership.
            if (!adminCourseAssignmentRepository.existsByLecturerIdAndCourseId(inviter.getId(), course.getId())) {
                throw new IllegalArgumentException("Only the admin who created this course can invite co-admins.");
            }
            course.setCreatedBy(inviter);
            courseRepository.save(course);
        } else {
            List<Lecturer> inviterRows = CourseScopeService.resolveLecturersByIdentity(lecturerRepository, inviterIdentity);
            boolean matchesCreator = inviterRows.stream()
                    .anyMatch(l -> l.getId() != null && l.getId().equals(course.getCreatedBy().getId()));
            if (!matchesCreator) {
                throw new IllegalArgumentException("Only the admin who created this course can invite co-admins.");
            }
        }

        String normalizedEmail = invitedAdminEmail.trim().toLowerCase(Locale.ROOT);

        boolean existsAsAdminAccount = adminRepository.findByEmail(normalizedEmail).isPresent()
                || superAdminRepository.findByEmail(normalizedEmail).isPresent();

        List<Lecturer> invitedMatches = CourseScopeService.resolveLecturersByIdentity(lecturerRepository, normalizedEmail);
        Lecturer invitedLecturer = invitedMatches.stream()
                .filter(l -> l.getRoles() != null && l.getRoles().contains("ROLE_ADMIN"))
                .findFirst()
                .orElse(invitedMatches.isEmpty() ? null : invitedMatches.get(0));
        boolean existsAsAdminLecturer = invitedLecturer != null
                && invitedLecturer.getRoles() != null
                && invitedLecturer.getRoles().contains("ROLE_ADMIN");

        if (!existsAsAdminAccount && !existsAsAdminLecturer) {
            throw new IllegalArgumentException("Invited user must be an existing admin account.");
        }

        Lecturer invitedAdmin = invitedLecturer;
        if (invitedAdmin == null) {
            // This method was removed. We need to create a lecturer record if it doesn't exist.
            // For simplicity, we'll throw an exception if the admin doesn't also exist as a lecturer.
            // A more robust solution would be to create the lecturer record here.
            throw new IllegalArgumentException("Invited admin does not have a corresponding lecturer profile.");
        }

        if (invitedAdmin.getRoles() == null || !invitedAdmin.getRoles().contains("ROLE_ADMIN")) {
            throw new IllegalArgumentException("Invited user is not an admin.");
        }

        saveAdminAssignment(invitedAdmin, course);

        if (invitedAdmin.getCourse() == null) {
            invitedAdmin.setCourse(course);
            lecturerRepository.save(invitedAdmin);
        }
    }

    public Optional<Lecturer> resolveLecturerByIdentity(String identity) {
        if (identity == null || identity.isBlank()) {
            return Optional.empty();
        }

        String normalized = identity.trim();
        List<Lecturer> matched = CourseScopeService.resolveLecturersByIdentity(lecturerRepository, normalized);
        Optional<Lecturer> lecturerOpt = matched.isEmpty() ? Optional.empty() : Optional.of(matched.get(0));

        if (lecturerOpt.isEmpty()) {
            Optional<Admin> adminOpt = adminRepository.findByEmail(normalized);
            if (adminOpt.isEmpty()) {
                adminOpt = adminRepository.findByUsername(normalized);
            }
            if (adminOpt.isPresent()) {
                Admin admin = adminOpt.get();
                Lecturer newLecturer = new Lecturer();
                newLecturer.setEmail(admin.getEmail());
                newLecturer.setUsername(admin.getUsername());
                newLecturer.setPassword(admin.getPassword());
                newLecturer.setRoles("ROLE_ADMIN,ROLE_LECTURER");
                newLecturer.setIsTempPassword(false);
                newLecturer.setResetPasswordToken(admin.getResetPasswordToken());
                Lecturer saved = lecturerRepository.save(newLecturer);
                return Optional.of(saved);
            }

            try {
                ensureSuperAdminAssignable(normalized);
                List<Lecturer> created = CourseScopeService.resolveLecturersByIdentity(lecturerRepository, normalized);
                return created.isEmpty() ? Optional.empty() : Optional.of(created.get(0));
            } catch (Exception e) {
                // Not a super admin or missing from super admin table, ignore
            }
        }
        
        return lecturerOpt;
    }

    @Transactional
    public void resetToSuperAdminOnly() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("Database reset is unavailable: JdbcTemplate not configured.");
        }

        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
            deleteTableRows("student_result_overrides");
            deleteTableRows("marks");
            deleteTableRows("assessment_comments");
            deleteTableRows("lecturer_rubric_assignments");
            deleteTableRows("lecturer_group_assignment");
            deleteTableRows("lecturer_student_assignment");
            deleteTableRows("student_assessment_assignment");
            deleteTableRows("admin_course_assignment");
            deleteTableRows("student_course_assignment");
            deleteTableRows("deadlines");
            deleteTableRows("rating");
            deleteTableRows("sub_rubric");
            deleteTableRows("rubric");
            deleteTableRows("assessment");
            deleteTableRows("student");
            deleteTableRows("project_group");
            deleteTableRows("rubric_templates");
            deleteTableRows("industrial_supervisor");
            deleteTableRows("admin");
            deleteTableRows("lecturer");
            deleteTableRows("courses");
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    private void deleteTableRows(String tableName) {
        jdbcTemplate.execute("DELETE FROM `" + tableName + "`");
    }

    /** Gives an admin the course as an admin; a row that only made them an assessor there is upgraded, not duplicated. */
    private void saveAdminAssignment(Lecturer admin, Course course) {
        AdminCourseAssignment assignment = adminCourseAssignmentRepository.findByLecturerId(admin.getId()).stream()
            .filter(a -> a.getCourse() != null && course.getId().equals(a.getCourse().getId()))
            .findFirst()
            .orElseGet(AdminCourseAssignment::new);
        assignment.setLecturer(admin);
        assignment.setCourse(course);
        assignment.setAsAdmin(Boolean.TRUE);
        adminCourseAssignmentRepository.save(assignment);
    }
}
