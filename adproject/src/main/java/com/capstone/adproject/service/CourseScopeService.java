package com.capstone.adproject.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.StudentCourseAssignment;
import com.capstone.adproject.repositories.AdminCourseAssignmentRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.StudentCourseAssignmentRepository;
import com.capstone.adproject.repositories.StudentRepository;

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
    private StudentRepository studentRepository;
    private StudentCourseAssignmentRepository studentCourseAssignmentRepository;

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

    @org.springframework.beans.factory.annotation.Autowired
    public CourseScopeService(
            LecturerRepository lecturerRepository,
            AdminCourseAssignmentRepository adminCourseAssignmentRepository,
            SuperAdminService superAdminService,
            JdbcTemplate jdbcTemplate,
            @org.springframework.beans.factory.annotation.Autowired(required = false) StudentRepository studentRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) StudentCourseAssignmentRepository studentCourseAssignmentRepository) {
        this.lecturerRepository = lecturerRepository;
        this.adminCourseAssignmentRepository = adminCourseAssignmentRepository;
        this.superAdminService = superAdminService;
        this.jdbcTemplate = jdbcTemplate;
        this.studentRepository = studentRepository;
        this.studentCourseAssignmentRepository = studentCourseAssignmentRepository;
    }

    public List<Course> getManagedCoursesForCurrentUser() {
        String username = getLoggedInUsername();
        if (username == null || username.isBlank()) {
            return Collections.emptyList();
        }

        List<Lecturer> lecturers = resolveLecturersByIdentity(lecturerRepository, username);
        if (lecturers.isEmpty()) {
            return getEnrolledCoursesForStudent(username);
        }

        Set<Course> allManagedCourses = new LinkedHashSet<>();
        List<Course> allCourses = superAdminService != null ? superAdminService.getAllCourses() : Collections.emptyList();

        for (Lecturer lecturer : lecturers) {
            Course directCourse = safeCourseFromLecturer(lecturer);
            if (directCourse != null && directCourse.getId() != null) {
                allManagedCourses.add(directCourse);
            }

            Set<Long> assignedCourseIds = findAssignedCourseIdsForLecturer(lecturer.getId());
            List<Course> assignments = loadCoursesByIds(assignedCourseIds).stream()
                .filter(c -> c != null && c.getId() != null)
                .collect(Collectors.toList());

            if (assignments.isEmpty() && adminCourseAssignmentRepository != null) {
                assignments = adminCourseAssignmentRepository.findByLecturerId(lecturer.getId())
                    .stream()
                    .map(this::safeCourseFromAssignment)
                    .filter(c -> c != null && c.getId() != null)
                    .collect(Collectors.toList());
            }

            allManagedCourses.addAll(deduplicateById(assignments));

            if (allCourses != null) {
                List<Course> createdCourses = allCourses.stream()
                    .filter(c -> c != null && c.getId() != null && c.getCreatedBy() != null
                              && c.getCreatedBy().getId() != null
                              && c.getCreatedBy().getId().equals(lecturer.getId()))
                    .collect(Collectors.toList());
                allManagedCourses.addAll(createdCourses);
            }
        }

        List<Course> studentCourses = getEnrolledCoursesForStudent(username);
        allManagedCourses.addAll(studentCourses);

        return sortCourses(deduplicateById(new ArrayList<>(allManagedCourses)));
    }

    /**
     * The courses the current user administers: their own course, the ones they created, and the ones they were invited to as
     * an admin. Courses where they are only an assessor or a student are not here, even though they can work in them.
     */
    public Set<Long> getAdminCourseIdsForCurrentUser() {
        String username = getLoggedInUsername();
        if (username == null || username.isBlank()) {
            return Collections.emptySet();
        }
        Set<Long> ids = new LinkedHashSet<>();
        List<Course> allCourses = superAdminService != null ? superAdminService.getAllCourses() : Collections.emptyList();
        for (Lecturer lecturer : resolveLecturersByIdentity(lecturerRepository, username)) {
            Course home = safeCourseFromLecturer(lecturer);
            if (home != null && home.getId() != null) {
                ids.add(home.getId());
            }
            if (allCourses != null) {
                for (Course c : allCourses) {
                    if (c != null && c.getId() != null && c.getCreatedBy() != null && lecturer.getId() != null
                            && lecturer.getId().equals(c.getCreatedBy().getId())) {
                        ids.add(c.getId());
                    }
                }
            }
            if (adminCourseAssignmentRepository != null && lecturer.getId() != null) {
                for (AdminCourseAssignment assignment : adminCourseAssignmentRepository.findByLecturerId(lecturer.getId())) {
                    Course c = safeCourseFromAssignment(assignment);
                    if (c != null && c.getId() != null && assignment.grantsAdminOfCourse()) {
                        ids.add(c.getId());
                    }
                }
            }
        }
        return ids;
    }

    /**
     * Whether the person may act as an admin in this course. Someone with admin links to some courses may do so only there;
     * an account with none (a super admin, or an old account without links) keeps using its managed courses.
     */
    public boolean canActAsAdminInCourse(Long courseId) {
        if (courseId == null) {
            return false;
        }
        Set<Long> adminIds = getAdminCourseIdsForCurrentUser();
        return adminIds.isEmpty() ? isManagedCourseId(courseId) : adminIds.contains(courseId);
    }

    /** The courses to offer in the admin area: where the person is an admin, not the ones they only assess or study in. */
    public List<Course> getAdminAreaCoursesForCurrentUser() {
        Set<Long> adminIds = getAdminCourseIdsForCurrentUser();
        List<Course> managed = getManagedCoursesForCurrentUser();
        if (adminIds.isEmpty()) {
            return managed;
        }
        return managed.stream().filter(c -> c != null && adminIds.contains(c.getId())).collect(Collectors.toList());
    }

    /**
     * Called when an admin page is requested. If the course the person was last working in is one they only assess or study
     * in, move them to one of their own admin courses (their home course first), so admin pages never show that course.
     */
    public Long ensureAdminActiveCourse() {
        Set<Long> adminIds = getAdminCourseIdsForCurrentUser();
        Long current = getActiveCourseIdForCurrentUser();
        if (adminIds.isEmpty() || (current != null && adminIds.contains(current))) {
            return current;
        }
        Long preferred = getPreferredActiveCourseIdFromProfile(adminIds);
        Long target = preferred != null ? preferred : adminIds.iterator().next();
        return setActiveCourseIdForCurrentUser(target) ? target : current;
    }

    public boolean isAdminOfCourse(Long courseId) {
        return courseId != null && getAdminCourseIdsForCurrentUser().contains(courseId);
    }

    public List<Course> getEnrolledCoursesForStudent(String username) {
        if (studentRepository == null || username == null || username.isBlank()) {
            return Collections.emptyList();
        }

        Set<Course> courses = new LinkedHashSet<>();
        List<Student> students = new ArrayList<>();
        
        try {
            List<Student> byEmail = studentRepository.findAllByEmailIgnoreCase(username);
            if (byEmail != null) students.addAll(byEmail);
        } catch (Exception ignored) {
            studentRepository.findByEmail(username).ifPresent(students::add);
        }
        try {
            List<Student> byUsername = studentRepository.findAllByUsernameIgnoreCase(username);
            if (byUsername != null) {
                for (Student s : byUsername) {
                    if (!students.contains(s)) students.add(s);
                }
            }
        } catch (Exception ignored) {
            studentRepository.findByUsername(username).ifPresent(s -> {
                if (!students.contains(s)) students.add(s);
            });
        }

        for (Student student : students) {
            if (student.getCourse() != null && student.getCourse().getId() != null) {
                courses.add(student.getCourse());
            }
            if (student.getGroup() != null && student.getGroup().getCourse() != null && student.getGroup().getCourse().getId() != null) {
                courses.add(student.getGroup().getCourse());
            }
            if (studentCourseAssignmentRepository != null && student.getId() != null) {
                List<StudentCourseAssignment> assignments = studentCourseAssignmentRepository.findByStudentId(student.getId());
                if (assignments != null) {
                    for (StudentCourseAssignment sca : assignments) {
                        if (sca.getCourse() != null && sca.getCourse().getId() != null) {
                            courses.add(sca.getCourse());
                        }
                    }
                }
            }
        }

        return sortCourses(deduplicateById(new ArrayList<>(courses)));
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

    public List<Course> getAllCoursesForSuperAdmin() {
        if (!isSuperAdmin()) {
            return Collections.emptyList();
        }
        return sortCourses(superAdminService.getAllCourses());
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

    public static List<Lecturer> resolveLecturersByIdentity(LecturerRepository repository, String identifier) {
        if (repository == null || identifier == null || identifier.isBlank()) {
            return Collections.emptyList();
        }
        Map<Long, Lecturer> found = new LinkedHashMap<>();
        addLecturers(found, repository.findAllByEmailIgnoreCase(identifier));
        addLecturers(found, repository.findAllByUsernameIgnoreCase(identifier));

        if (found.isEmpty()) {
            safeSingleLookup(() -> repository.findByEmailIgnoreCase(identifier)).ifPresent(l -> addLecturer(found, l));
            safeSingleLookup(() -> repository.findByUsernameIgnoreCase(identifier)).ifPresent(l -> addLecturer(found, l));
            safeSingleLookup(() -> repository.findByEmail(identifier)).ifPresent(l -> addLecturer(found, l));
            safeSingleLookup(() -> repository.findByUsername(identifier)).ifPresent(l -> addLecturer(found, l));
        }

        if (found.isEmpty()) {
            int atIndex = identifier.indexOf('@');
            if (atIndex > 0) {
                String localPart = identifier.substring(0, atIndex);
                addLecturers(found, repository.findAllByUsernameIgnoreCase(localPart));
                if (found.isEmpty()) {
                    safeSingleLookup(() -> repository.findByUsernameIgnoreCase(localPart)).ifPresent(l -> addLecturer(found, l));
                    safeSingleLookup(() -> repository.findByUsername(localPart)).ifPresent(l -> addLecturer(found, l));
                }
            }
        }
        return new ArrayList<>(found.values());
    }

    public static Optional<Lecturer> resolveLecturerForCourse(LecturerRepository repository, String identifier, Long courseId) {
        List<Lecturer> candidates = resolveLecturersByIdentity(repository, identifier);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (courseId != null) {
            for (Lecturer candidate : candidates) {
                if (candidate.getCourse() != null && courseId.equals(candidate.getCourse().getId())) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.of(candidates.get(0));
    }

    public static List<Student> resolveStudentsByIdentity(StudentRepository repository, String identifier) {
        if (repository == null || identifier == null || identifier.isBlank()) {
            return Collections.emptyList();
        }
        Map<Long, Student> found = new LinkedHashMap<>();
        addStudents(found, repository.findAllByEmailIgnoreCase(identifier));
        addStudents(found, repository.findAllByUsernameIgnoreCase(identifier));

        if (found.isEmpty()) {
            safeSingleLookupStudent(() -> repository.findFirstByEmailIgnoreCaseOrderByIdAsc(identifier)).ifPresent(s -> addStudent(found, s));
            safeSingleLookupStudent(() -> repository.findByEmail(identifier)).ifPresent(s -> addStudent(found, s));
            safeSingleLookupStudent(() -> repository.findByUsername(identifier)).ifPresent(s -> addStudent(found, s));
        }

        if (found.isEmpty()) {
            int atIndex = identifier.indexOf('@');
            if (atIndex > 0) {
                String localPart = identifier.substring(0, atIndex);
                addStudents(found, repository.findAllByUsernameIgnoreCase(localPart));
                if (found.isEmpty()) {
                    safeSingleLookupStudent(() -> repository.findByUsername(localPart)).ifPresent(s -> addStudent(found, s));
                }
            }
        }
        return new ArrayList<>(found.values());
    }

    public static Optional<Student> resolveStudentForCourse(StudentRepository repository, String identifier, Long courseId) {
        List<Student> candidates = resolveStudentsByIdentity(repository, identifier);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (courseId != null) {
            for (Student candidate : candidates) {
                if (candidate.getCourse() != null && courseId.equals(candidate.getCourse().getId())) {
                    return Optional.of(candidate);
                }
                if (candidate.getGroup() != null && candidate.getGroup().getCourse() != null
                        && courseId.equals(candidate.getGroup().getCourse().getId())) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.of(candidates.get(0));
    }

    public Optional<Student> getActiveStudentForCurrentUser() {
        String username = getLoggedInUsername();
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        Long activeCourseId = getActiveCourseIdForCurrentUser();
        return resolveStudentForCourse(studentRepository, username, activeCourseId);
    }

    public Optional<Lecturer> getActiveLecturerForCurrentUser() {
        String username = getLoggedInUsername();
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        Long activeCourseId = getActiveCourseIdForCurrentUser();
        Optional<Lecturer> inActive = resolveLecturerForCourse(lecturerRepository, username, activeCourseId);
        return inActive.isPresent() ? inActive : findCurrentLecturer(username);
    }

    private static void addLecturers(Map<Long, Lecturer> target, List<Lecturer> source) {
        if (source == null) {
            return;
        }
        for (Lecturer lecturer : source) {
            addLecturer(target, lecturer);
        }
    }

    private static void addLecturer(Map<Long, Lecturer> target, Lecturer lecturer) {
        if (lecturer == null) {
            return;
        }
        Long key = lecturer.getId() != null ? lecturer.getId() : -1L - target.size();
        target.putIfAbsent(key, lecturer);
    }

    private static Optional<Lecturer> safeSingleLookup(java.util.function.Supplier<Optional<Lecturer>> supplier) {
        try {
            Optional<Lecturer> result = supplier.get();
            return result != null ? result : Optional.empty();
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static void addStudents(Map<Long, Student> target, List<Student> source) {
        if (source == null) {
            return;
        }
        for (Student student : source) {
            addStudent(target, student);
        }
    }

    private static void addStudent(Map<Long, Student> target, Student student) {
        if (student == null) {
            return;
        }
        Long key = student.getId() != null ? student.getId() : -1L - target.size();
        target.putIfAbsent(key, student);
    }

    private static Optional<Student> safeSingleLookupStudent(java.util.function.Supplier<Optional<Student>> supplier) {
        try {
            Optional<Student> result = supplier.get();
            return result != null ? result : Optional.empty();
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private Optional<Lecturer> findCurrentLecturer(String username) {
        List<Lecturer> candidates = resolveLecturersByIdentity(lecturerRepository, username);
        return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(0));
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
        // Active course selection is session-scoped only so switching courses
        // never mutates lecturer.course_id in the database.
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

        Set<Long> result = new LinkedHashSet<>();
        try {
            List<Long> ids = jdbcTemplate.query(
                "SELECT DISTINCT course_id FROM admin_course_assignment WHERE lecturer_id = ? AND course_id IS NOT NULL",
                (rs, rowNum) -> rs.getLong(1),
                lecturerId
            );
            if (ids != null) {
                ids.stream().filter(Objects::nonNull).forEach(result::add);
            }
        } catch (Exception ignored) {}

        try {
            List<Long> evalCourseIds = jdbcTemplate.query(
                "SELECT DISTINCT a.course_id FROM lecturer_student_assignment lsa JOIN assessment a ON a.id = lsa.assessment_id WHERE lsa.lecturer_id = ? AND a.course_id IS NOT NULL",
                (rs, rowNum) -> rs.getLong(1),
                lecturerId
            );
            if (evalCourseIds != null) {
                evalCourseIds.stream().filter(Objects::nonNull).forEach(result::add);
            }
        } catch (Exception ignored) {}

        try {
            List<Long> rubricCourseIds = jdbcTemplate.query(
                "SELECT DISTINCT a.course_id FROM lecturer_rubric_assignments lra JOIN assessment a ON a.id = lra.assessment_id WHERE lra.lecturer_id = ? AND a.course_id IS NOT NULL",
                (rs, rowNum) -> rs.getLong(1),
                lecturerId
            );
            if (rubricCourseIds != null) {
                rubricCourseIds.stream().filter(Objects::nonNull).forEach(result::add);
            }
        } catch (Exception ignored) {}

        try {
            List<Long> groupAssignedIds = jdbcTemplate.query(
                "SELECT DISTINCT a.course_id FROM lecturer_group_assignment lga JOIN assessment a ON a.id = lga.assessment_id WHERE lga.lecturer_id = ? AND a.course_id IS NOT NULL",
                (rs, rowNum) -> rs.getLong(1),
                lecturerId
            );
            if (groupAssignedIds != null) {
                groupAssignedIds.stream().filter(Objects::nonNull).forEach(result::add);
            }
        } catch (Exception ignored) {}

        try {
            List<Long> supervisorCourseIds = jdbcTemplate.query(
                "SELECT DISTINCT course_id FROM project_group WHERE (academic_supervisor_id = ? OR industrial_supervisor_id = ?) AND course_id IS NOT NULL",
                (rs, rowNum) -> rs.getLong(1),
                lecturerId, lecturerId
            );
            if (supervisorCourseIds != null) {
                supervisorCourseIds.stream().filter(Objects::nonNull).forEach(result::add);
            }
        } catch (Exception ignored) {}

        return result;
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