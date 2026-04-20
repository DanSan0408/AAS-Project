package com.capstone.adproject.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.model.AdminCourseAssignment;
import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.SuperAdmin;
import com.capstone.adproject.repositories.AdminCourseAssignmentRepository;
import com.capstone.adproject.repositories.CourseRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.SuperAdminRepository;
import com.capstone.adproject.util.PasswordGenerator;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class SuperAdminService {

    private final CourseRepository courseRepository;
    private final LecturerRepository lecturerRepository;
    private final SuperAdminRepository superAdminRepository;
    private final AdminCourseAssignmentRepository adminCourseAssignmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final CourseStatisticsService courseStatisticsService;

    @PersistenceContext
    private EntityManager entityManager;

    public SuperAdminService(CourseRepository courseRepository, LecturerRepository lecturerRepository,
            SuperAdminRepository superAdminRepository,
            AdminCourseAssignmentRepository adminCourseAssignmentRepository,
            PasswordEncoder passwordEncoder, EmailService emailService, CourseStatisticsService courseStatisticsService) {
        this.courseRepository = courseRepository;
        this.lecturerRepository = lecturerRepository;
        this.superAdminRepository = superAdminRepository;
        this.adminCourseAssignmentRepository = adminCourseAssignmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.courseStatisticsService = courseStatisticsService;
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

        Optional<Lecturer> existingLecturer = lecturerRepository.findByEmail(superAdminEmail);
        if (existingLecturer.isPresent()) {
            Lecturer lecturer = existingLecturer.get();
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

        // Idempotent create: if same course code already exists, reuse it instead of inserting duplicates.
        if (course.getId() == null && course.getCourseCode() != null && !course.getCourseCode().isBlank()) {
            Optional<Course> existing = courseRepository.findByCourseCodeIgnoreCase(course.getCourseCode());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        return courseRepository.save(course);
    }

        @Transactional
        public void deleteCourse(Long id) {
        entityManager.flush();
        entityManager.clear();

        // Delete rubric templates first (has FK to courses)
        entityManager.createNativeQuery("DELETE FROM `rubric_templates` WHERE course_id = :courseId")
            .setParameter("courseId", id)
            .executeUpdate();

        // Detach all known references before deleting the course row.
        entityManager.createNativeQuery("DELETE FROM `admin_course_assignment` WHERE course_id = :courseId")
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

        entityManager.createNativeQuery("UPDATE `Assessment` SET course_id = NULL WHERE course_id = :courseId")
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
        Optional<Lecturer> lecturerOpt = lecturerRepository.findByEmail(email);
        if (lecturerOpt.isEmpty()) {
            return List.of();
        }

        Lecturer lecturer = lecturerOpt.get();
        List<Course> assigned = adminCourseAssignmentRepository.findByLecturerId(lecturer.getId())
                .stream()
                .map(AdminCourseAssignment::getCourse)
                .filter(c -> c != null)
                .collect(Collectors.toList());

        if (assigned.isEmpty() && lecturer.getCourse() != null) {
            return List.of(lecturer.getCourse());
        }

        return assigned;
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

    @Transactional
    public void assignAdminToCourse(Long lecturerId, Long courseId) {
        Lecturer admin = lecturerRepository.findById(lecturerId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found."));

        if (admin.getRoles() == null || !admin.getRoles().contains("ROLE_ADMIN")) {
            throw new IllegalArgumentException("Selected user is not an admin.");
        }

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found."));

        if (!adminCourseAssignmentRepository.existsByLecturerIdAndCourseId(lecturerId, courseId)) {
            AdminCourseAssignment assignment = new AdminCourseAssignment();
            assignment.setLecturer(admin);
            assignment.setCourse(course);
            adminCourseAssignmentRepository.save(assignment);
        }

        // Keep legacy single-course field populated for backward compatibility.
        if (admin.getCourse() == null) {
            admin.setCourse(course);
            lecturerRepository.save(admin);
        }
    }

    @Transactional
    public void inviteAdmin(String email, String name, Long courseId) {
        Course course = null;
        if (courseId != null) {
            course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new IllegalArgumentException("Course not found with ID: " + courseId));
        }

        // Check if a lecturer with this email already exists
        Optional<Lecturer> existingLecturer = lecturerRepository.findByEmail(email);
        Lecturer newAdmin;

        if (existingLecturer.isPresent()) {
            newAdmin = existingLecturer.get();
            // Update roles if not already an admin
            if (!newAdmin.getRoles().contains("ROLE_ADMIN")) {
                newAdmin.setRoles(newAdmin.getRoles() + ",ROLE_ADMIN");
            }
        } else {
            newAdmin = new Lecturer();
            newAdmin.setEmail(email);
            newAdmin.setUsername(name); // Use name as username for new lecturer
            String tempPassword = PasswordGenerator.generateRandomPassword();
            newAdmin.setPassword(passwordEncoder.encode(tempPassword));
            newAdmin.setIsTempPassword(true);
            newAdmin.setResetPasswordToken(UUID.randomUUID().toString());
            newAdmin.setRoles("ROLE_LECTURER,ROLE_ADMIN"); // New admin is also a lecturer
            emailService.sendWelcomeEmailWithPassword(email, tempPassword, "Admin", "http://localhost:8080/reset_password?token=" + newAdmin.getResetPasswordToken()); // Placeholder URL
        }

        if (course != null) {
            newAdmin.setCourse(course); // Legacy fallback
        }

        Lecturer savedAdmin = lecturerRepository.save(newAdmin);

        if (course != null && !adminCourseAssignmentRepository.existsByLecturerIdAndCourseId(savedAdmin.getId(), course.getId())) {
            AdminCourseAssignment assignment = new AdminCourseAssignment();
            assignment.setLecturer(savedAdmin);
            assignment.setCourse(course);
            adminCourseAssignmentRepository.save(assignment);
        }
    }
}