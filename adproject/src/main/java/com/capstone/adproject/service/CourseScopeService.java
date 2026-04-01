package com.capstone.adproject.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.capstone.adproject.model.AdminCourseAssignment;
import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.repositories.AdminCourseAssignmentRepository;
import com.capstone.adproject.repositories.LecturerRepository;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Service
public class CourseScopeService {

    public static final String ACTIVE_COURSE_SESSION_KEY = "activeCourseId";

    private final LecturerRepository lecturerRepository;
    private final AdminCourseAssignmentRepository adminCourseAssignmentRepository;
    private final SuperAdminService superAdminService;
    private final JdbcTemplate jdbcTemplate;

    public CourseScopeService(
            LecturerRepository lecturerRepository,
            AdminCourseAssignmentRepository adminCourseAssignmentRepository,
            SuperAdminService superAdminService,
            JdbcTemplate jdbcTemplate) {
        this.lecturerRepository = lecturerRepository;
        this.adminCourseAssignmentRepository = adminCourseAssignmentRepository;
        this.superAdminService = superAdminService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Course> getManagedCoursesForCurrentUser() {
        String username = getLoggedInUsername();
        if (username == null || username.isBlank()) {
            return Collections.emptyList();
        }

        if (isSuperAdmin()) {
            return sortCourses(superAdminService.getAllCourses());
        }

        List<Course> managed = findCurrentLecturer(username)
            .map(lecturer -> {
                // Step 1: Try to get courses from admin_course_assignment table via direct SQL
                Set<Long> assignedCourseIds = findAssignedCourseIdsForLecturer(lecturer.getId());
                List<Course> assignments = loadCoursesByIds(assignedCourseIds).stream()
                    .filter(c -> c != null && c.getId() != null)
                    .collect(Collectors.toList());

                // Step 2: If no assignments found, try JPA fallback
                if (assignments.isEmpty()) {
                    assignments = adminCourseAssignmentRepository.findByLecturerId(lecturer.getId())
                        .stream()
                        .map(this::safeCourseFromAssignment)
                        .filter(c -> c != null && c.getId() != null)
                        .collect(Collectors.toList());
                }

                // Step 3: If still empty, check if lecturer has a direct course link
                Course legacyCourse = safeCourseFromLecturer(lecturer);
                if (assignments.isEmpty() && legacyCourse != null && legacyCourse.getId() != null) {
                    assignments = List.of(legacyCourse);
                }

                return deduplicateById(assignments);
            })
            .orElse(Collections.emptyList());

        return sortCourses(managed);
    }

    public Set<Long> getManagedCourseIdsForCurrentUser() {
        return getManagedCoursesForCurrentUser().stream()
            .map(Course::getId)
            .filter(id -> id != null)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Long getActiveCourseIdForCurrentUser() {
        List<Course> managedCourses = getManagedCoursesForCurrentUser();
        if (managedCourses.isEmpty()) {
            clearActiveCourseFromSession();
            return null;
        }

        Set<Long> managedIds = managedCourses.stream()
            .map(Course::getId)
            .filter(id -> id != null)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        Long current = getActiveCourseIdFromSession();
        if (current != null && managedIds.contains(current)) {
            return current;
        }

        Long preferred = getPreferredActiveCourseIdFromProfile(managedIds);
        if (preferred != null) {
            setActiveCourseIdForCurrentUser(preferred);
            return preferred;
        }

        Long fallback = managedCourses.get(0).getId();
        setActiveCourseIdForCurrentUser(fallback);
        return fallback;
    }

    public Course getActiveCourseForCurrentUser() {
        Long activeId = getActiveCourseIdForCurrentUser();
        if (activeId == null) {
            return null;
        }

        return getManagedCoursesForCurrentUser().stream()
            .filter(c -> activeId.equals(c.getId()))
            .findFirst()
            .orElse(null);
    }

    public Set<Long> getActiveCourseIdsForCurrentUser() {
        Long activeId = getActiveCourseIdForCurrentUser();
        if (activeId == null) {
            return Collections.emptySet();
        }
        return Set.of(activeId);
    }

    public boolean isManagedCourseId(Long courseId) {
        return courseId != null && getManagedCourseIdsForCurrentUser().contains(courseId);
    }

    public boolean isActiveCourseId(Long courseId) {
        Long activeId = getActiveCourseIdForCurrentUser();
        return courseId != null && activeId != null && activeId.equals(courseId);
    }

    public boolean setActiveCourseIdForCurrentUser(Long courseId) {
        if (!isManagedCourseId(courseId)) {
            return false;
        }

        HttpSession session = getCurrentSession(true);
        if (session == null) {
            return false;
        }

        session.setAttribute(ACTIVE_COURSE_SESSION_KEY, courseId);
        persistPreferredActiveCourse(courseId);
        return true;
    }

    private String getLoggedInUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return authentication.getName();
    }

    private boolean isSuperAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
            .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
    }

    private Long getActiveCourseIdFromSession() {
        HttpSession session = getCurrentSession(false);
        if (session == null) {
            return null;
        }

        Object value = session.getAttribute(ACTIVE_COURSE_SESSION_KEY);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private void clearActiveCourseFromSession() {
        HttpSession session = getCurrentSession(false);
        if (session != null) {
            session.removeAttribute(ACTIVE_COURSE_SESSION_KEY);
        }
    }

    private HttpSession getCurrentSession(boolean create) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }

        HttpServletRequest request = attrs.getRequest();
        return request.getSession(create);
    }

    private List<Course> deduplicateById(List<Course> courses) {
        return new ArrayList<>(courses.stream()
            .filter(c -> c != null && c.getId() != null)
            .collect(Collectors.toMap(Course::getId, c -> c, (a, b) -> a))
            .values());
    }

    private Optional<Lecturer> findCurrentLecturer(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        Optional<Lecturer> found = lecturerRepository.findByEmailIgnoreCase(username)
            .or(() -> lecturerRepository.findByUsernameIgnoreCase(username))
            .or(() -> lecturerRepository.findByEmail(username))
            .or(() -> lecturerRepository.findByUsername(username));

        if (found.isPresent()) {
            return found;
        }

        int atIndex = username.indexOf('@');
        if (atIndex > 0) {
            String localPart = username.substring(0, atIndex);
            return lecturerRepository.findByUsernameIgnoreCase(localPart)
                .or(() -> lecturerRepository.findByUsername(localPart));
        }

        return Optional.empty();
    }

    private Long getPreferredActiveCourseIdFromProfile(Set<Long> managedIds) {
        String username = getLoggedInUsername();
        return findCurrentLecturer(username)
            .map(this::safeCourseFromLecturer)
            .filter(c -> c != null && c.getId() != null)
            .map(Course::getId)
            .filter(id -> id != null && managedIds.contains(id))
            .orElse(null);
    }

    private Course safeCourseFromAssignment(AdminCourseAssignment assignment) {
        if (assignment == null) {
            return null;
        }

        try {
            Course course = assignment.getCourse();
            if (course == null || course.getId() == null) {
                return null;
            }
            return course;
        } catch (EntityNotFoundException | JpaObjectRetrievalFailureException ex) {
            return null;
        }
    }

    private Course safeCourseFromLecturer(Lecturer lecturer) {
        if (lecturer == null) {
            return null;
        }

        try {
            Course course = lecturer.getCourse();
            if (course == null || course.getId() == null) {
                return null;
            }
            return course;
        } catch (EntityNotFoundException | JpaObjectRetrievalFailureException ex) {
            return null;
        }
    }

    private void persistPreferredActiveCourse(Long courseId) {
        String username = getLoggedInUsername();
        if (username == null || username.isBlank() || courseId == null) {
            return;
        }

        findCurrentLecturer(username).ifPresent(lecturer -> {
            Course current = lecturer.getCourse();
            if (current != null && courseId.equals(current.getId())) {
                return;
            }

            // Before persisting, ensure the course exists in the FK target table (courses table)
            try {
                ensureCourseForeignKeyExists(courseId);

                Course selected = superAdminService.getCourseById(courseId).orElse(null);
                if (selected == null) {
                    return;
                }

                lecturer.setCourse(selected);
                lecturerRepository.save(lecturer);
            } catch (RuntimeException e) {
                // Silently skip if FK constraint would fail; session tracking is sufficient
                // The admin can still work with the course in the current session
            }
        });
    }

    private List<Course> sortCourses(List<Course> courses) {
        List<Course> sorted = new ArrayList<>(courses == null ? Collections.emptyList() : courses);
        sorted.sort(
            Comparator.comparing((Course c) -> c.getCourseCode() == null ? "" : c.getCourseCode(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(c -> c.getCourseName() == null ? "" : c.getCourseName(), String.CASE_INSENSITIVE_ORDER)
        );
        return sorted;
    }

    private Set<Long> findAssignedCourseIdsForLecturer(Long lecturerId) {
        if (lecturerId == null) {
            return Collections.emptySet();
        }

        List<Long> ids = jdbcTemplate.query(
            "SELECT DISTINCT course_id FROM admin_course_assignment WHERE lecturer_id = ? AND course_id IS NOT NULL",
            (rs, rowNum) -> rs.getLong(1),
            lecturerId
        );

        return ids.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<Course> loadCoursesByIds(Set<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return Collections.emptyList();
        }

        String schema = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        String primaryTable = findCourseTableName(schema, "courses");
        String secondaryTable = findCourseTableName(schema, "course");

        List<Course> loaded = new ArrayList<>();
        Set<Long> remaining = new LinkedHashSet<>(courseIds);

        if (primaryTable != null) {
            List<Course> primary = queryCoursesByIds(primaryTable, remaining);
            loaded.addAll(primary);
            remaining.removeAll(primary.stream().map(Course::getId).collect(Collectors.toSet()));
        }

        if (!remaining.isEmpty() && secondaryTable != null && (primaryTable == null || !secondaryTable.equalsIgnoreCase(primaryTable))) {
            List<Course> secondary = queryCoursesByIds(secondaryTable, remaining);
            loaded.addAll(secondary);
            remaining.removeAll(secondary.stream().map(Course::getId).collect(Collectors.toSet()));
        }

        return deduplicateById(loaded);
    }

    private List<Course> queryCoursesByIds(String tableName, Set<Long> ids) {
        if (tableName == null || ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT id, courseName, courseCode, description FROM " + q(tableName) + " WHERE id IN (" + placeholders + ")";
        Object[] args = ids.toArray();

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Course course = new Course();
            course.setId(rs.getLong("id"));
            course.setCourseName(rs.getString("courseName"));
            course.setCourseCode(rs.getString("courseCode"));
            course.setDescription(rs.getString("description"));
            return course;
        }, args);
    }

    private String findCourseTableName(String schema, String preferredName) {
        if (schema == null || schema.isBlank()) {
            return null;
        }

        List<String> matches = jdbcTemplate.queryForList(
            """
            SELECT TABLE_NAME
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = ?
              AND LOWER(TABLE_NAME) = LOWER(?)
            LIMIT 1
            """,
            String.class,
            schema,
            preferredName
        );

        return matches.isEmpty() ? null : matches.get(0);
    }

    private String q(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    private void ensureCourseForeignKeyExists(Long courseId) {
        if (courseId == null) {
            return;
        }

        try {
            String schema = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            if (schema == null || schema.isBlank()) {
                return;
            }

            // Check if course exists in 'courses' table (the FK target for lecturer)
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM courses WHERE id = ?",
                Integer.class,
                courseId
            );

            if (count != null && count > 0) {
                return; // Course already exists in FK target table
            }

            // Course doesn't exist in 'courses' table, try to copy from 'course' table
            String sourceTable = findCourseTableName(schema, "course");
            if (sourceTable != null && !sourceTable.equalsIgnoreCase("courses")) {
                // Copy from source table to courses table
                jdbcTemplate.update(
                    "INSERT IGNORE INTO courses (id, courseName, courseCode, description) " +
                    "SELECT id, courseName, courseCode, description FROM " + q(sourceTable) + " WHERE id = ?",
                    courseId
                );
            }
        } catch (DataAccessException e) {
            // Silently continue; if we can't ensure FK, the save will fail with a clear error
        }
    }
}