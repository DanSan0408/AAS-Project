package com.capstone.adproject.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.capstone.adproject.model.AdminCourseAssignment;
import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.StudentCourseAssignment;
import com.capstone.adproject.repositories.AdminCourseAssignmentRepository;
import com.capstone.adproject.repositories.CourseRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.StudentCourseAssignmentRepository;
import com.capstone.adproject.repositories.StudentRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseScopeServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private AdminCourseAssignmentRepository adminCourseAssignmentRepository;

    @Mock
    private LecturerRepository lecturerRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private StudentCourseAssignmentRepository studentCourseAssignmentRepository;

    @Mock
    private SuperAdminService superAdminService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private CourseScopeService courseScopeService;

    private Course course1;
    private Course course2;
    private Lecturer lecturer;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "lecturer@example.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        MockHttpServletRequest request = new MockHttpServletRequest();
        session = new MockHttpSession();
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        course1 = new Course();
        course1.setId(1L);
        course1.setCourseName("Course A");
        course1.setCourseCode("SECJ101");

        course2 = new Course();
        course2.setId(2L);
        course2.setCourseName("Course B");
        course2.setCourseCode("SECJ202");

        lecturer = new Lecturer();
        lecturer.setId(10L);
        lecturer.setEmail("lecturer@example.com");
        lecturer.setCourse(course1);

        when(lecturerRepository.findByEmailIgnoreCase("lecturer@example.com")).thenReturn(Optional.of(lecturer));
        when(lecturerRepository.findByUsernameIgnoreCase("lecturer@example.com")).thenReturn(Optional.of(lecturer));
        when(superAdminService.resolveLecturerByIdentity("lecturer@example.com")).thenReturn(Optional.of(lecturer));
        when(superAdminService.getAllCourses()).thenReturn(Collections.emptyList());
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any()))
                .thenReturn(Collections.emptyList());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void testGetManagedCoursesForCurrentUser_WhenAssignedViaRepository() {
        AdminCourseAssignment assignment = new AdminCourseAssignment();
        assignment.setId(100L);
        assignment.setLecturer(lecturer);
        assignment.setCourse(course1);

        when(adminCourseAssignmentRepository.findByLecturerId(10L)).thenReturn(List.of(assignment));
        when(superAdminService.getAllCourses()).thenReturn(Collections.emptyList());

        List<Course> managed = courseScopeService.getManagedCoursesForCurrentUser();
        assertEquals(1, managed.size());
        assertEquals("Course A", managed.get(0).getCourseName());
    }

    @Test
    void testGetManagedCoursesForCurrentUser_IncludesCreatorOwnedCourses() {
        Course created = new Course();
        created.setId(3L);
        created.setCourseName("Created Course");
        created.setCourseCode("SECJ303");
        created.setCreatedBy(lecturer);

        when(adminCourseAssignmentRepository.findByLecturerId(10L)).thenReturn(Collections.emptyList());
        when(superAdminService.getAllCourses()).thenReturn(List.of(created));

        List<Course> managed = courseScopeService.getManagedCoursesForCurrentUser();
        assertTrue(managed.stream().anyMatch(c -> "Created Course".equals(c.getCourseName())));
    }

    @Test
    void testGetManagedCoursesForCurrentUser_UnauthenticatedReturnsEmpty() {
        SecurityContextHolder.clearContext();
        List<Course> managed = courseScopeService.getManagedCoursesForCurrentUser();
        assertTrue(managed.isEmpty());
    }

    @Test
    void testSetActiveCourseForCurrentUser_SetsSessionAttribute() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        session = new MockHttpSession();
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request), true);

        SecurityContextHolder.clearContext();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "lecturer@example.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course1));
        when(superAdminService.getCourseById(1L)).thenReturn(Optional.of(course1));
        AdminCourseAssignment assignment = new AdminCourseAssignment(100L, lecturer, course1);
        when(adminCourseAssignmentRepository.findByLecturerId(10L)).thenReturn(List.of(assignment));
        when(superAdminService.getAllCourses()).thenReturn(Collections.emptyList());

        boolean success = courseScopeService.setActiveCourseIdForCurrentUser(1L);
        assertTrue(success, "setActiveCourseIdForCurrentUser should return true");
        assertEquals(1L, session.getAttribute(CourseScopeService.ACTIVE_COURSE_SESSION_KEY));
    }

    @Test
    void testIsManagedCourseId_ReturnsTrueForManagedCourse() {
        AdminCourseAssignment assignment = new AdminCourseAssignment(100L, lecturer, course1);
        when(adminCourseAssignmentRepository.findByLecturerId(10L)).thenReturn(List.of(assignment));
        when(superAdminService.getAllCourses()).thenReturn(Collections.emptyList());

        assertTrue(courseScopeService.isManagedCourseId(1L));
        assertFalse(courseScopeService.isManagedCourseId(999L));
    }

    @Test
    void testIsActiveCourseId_MatchesActiveCourse() {
        AdminCourseAssignment assignment = new AdminCourseAssignment(200L, lecturer, course2);
        when(adminCourseAssignmentRepository.findByLecturerId(10L)).thenReturn(List.of(assignment));
        when(superAdminService.getAllCourses()).thenReturn(Collections.emptyList());
        session.setAttribute(CourseScopeService.ACTIVE_COURSE_SESSION_KEY, 2L);

        assertTrue(courseScopeService.isActiveCourseId(2L));
        assertFalse(courseScopeService.isActiveCourseId(1L));
    }

    @Test
    void testSetActiveCourseIdForCurrentUser_DoesNotMutateLecturerCourseInDatabase() {
        AdminCourseAssignment assignment = new AdminCourseAssignment(200L, lecturer, course2);
        when(courseRepository.findById(2L)).thenReturn(Optional.of(course2));
        when(superAdminService.getCourseById(2L)).thenReturn(Optional.of(course2));
        when(adminCourseAssignmentRepository.findByLecturerId(10L)).thenReturn(List.of(assignment));
        when(superAdminService.getAllCourses()).thenReturn(Collections.emptyList());

        boolean switched = courseScopeService.setActiveCourseIdForCurrentUser(2L);
        assertTrue(switched);
        assertEquals(2L, session.getAttribute(CourseScopeService.ACTIVE_COURSE_SESSION_KEY));
        assertEquals(1L, lecturer.getCourse().getId(), "Switching active course must not overwrite lecturer.course");
        verify(lecturerRepository, never()).save(any());
    }

    @Test
    void testGetManagedCoursesForCurrentUser_LecturerInMultipleCourses() {
        Lecturer lecturer2 = new Lecturer();
        lecturer2.setId(11L);
        lecturer2.setEmail("lecturer@example.com");
        lecturer2.setCourse(course2);

        when(lecturerRepository.findAllByEmailIgnoreCase("lecturer@example.com")).thenReturn(List.of(lecturer, lecturer2));
        when(adminCourseAssignmentRepository.findByLecturerId(anyLong())).thenReturn(Collections.emptyList());

        List<Course> managed = courseScopeService.getManagedCoursesForCurrentUser();
        assertEquals(2, managed.size());
        assertTrue(managed.stream().anyMatch(c -> c.getId().equals(1L)));
        assertTrue(managed.stream().anyMatch(c -> c.getId().equals(2L)));
    }

    @Test
    void testGetManagedCoursesForCurrentUser_StudentEnrolledInMultipleCourses() {
        SecurityContextHolder.clearContext();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "student@example.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        when(lecturerRepository.findAllByEmailIgnoreCase("student@example.com")).thenReturn(Collections.emptyList());
        when(lecturerRepository.findAllByUsernameIgnoreCase("student@example.com")).thenReturn(Collections.emptyList());

        Student student = new Student();
        student.setId(50L);
        student.setEmail("student@example.com");
        student.setCourse(course1);

        StudentCourseAssignment sca = new StudentCourseAssignment();
        sca.setId(501L);
        sca.setStudent(student);
        sca.setCourse(course2);

        when(studentRepository.findAllByEmailIgnoreCase("student@example.com")).thenReturn(List.of(student));
        when(studentCourseAssignmentRepository.findByStudentId(50L)).thenReturn(List.of(sca));

        List<Course> courses = courseScopeService.getManagedCoursesForCurrentUser();
        assertEquals(2, courses.size());
        assertTrue(courses.stream().anyMatch(c -> c.getId().equals(1L)));
        assertTrue(courses.stream().anyMatch(c -> c.getId().equals(2L)));

        boolean switched = courseScopeService.setActiveCourseIdForCurrentUser(2L);
        assertTrue(switched);
        assertEquals(2L, session.getAttribute(CourseScopeService.ACTIVE_COURSE_SESSION_KEY));
    }

    // --- Working in a course is not the same as administering it ---------------------------------------------------

    @Test
    void anAdminAdministersTheirOwnCourseAndOnesTheyCreated() {
        Course created = new Course();
        created.setId(3L);
        created.setCreatedBy(lecturer);
        when(adminCourseAssignmentRepository.findByLecturerId(10L)).thenReturn(Collections.emptyList());
        when(superAdminService.getAllCourses()).thenReturn(List.of(created));

        assertTrue(courseScopeService.isAdminOfCourse(1L));   // their own course
        assertTrue(courseScopeService.isAdminOfCourse(3L));   // created
        assertFalse(courseScopeService.isAdminOfCourse(99L));
    }

    @Test
    void aCourseTheyWereInvitedToAsAnAdminCountsAndSoDoesALegacyRowWithNoRoleRecorded() {
        AdminCourseAssignment invited = new AdminCourseAssignment(1L, lecturer, course2, Boolean.TRUE);
        Course course3 = new Course();
        course3.setId(3L);
        AdminCourseAssignment legacy = new AdminCourseAssignment(2L, lecturer, course3, null);
        when(adminCourseAssignmentRepository.findByLecturerId(10L)).thenReturn(List.of(invited, legacy));

        assertTrue(courseScopeService.isAdminOfCourse(2L));
        assertTrue(courseScopeService.isAdminOfCourse(3L));
    }

    @Test
    void aCourseTheyOnlyJoinedAsAnAssessorIsOneTheyCanWorkInButNotAdminister() {
        AdminCourseAssignment asAssessor = new AdminCourseAssignment(1L, lecturer, course2); // three-argument = assessor
        when(adminCourseAssignmentRepository.findByLecturerId(10L)).thenReturn(List.of(asAssessor));

        assertTrue(courseScopeService.getManagedCourseIdsForCurrentUser().contains(2L), "they can still switch to it and work in it");
        assertFalse(courseScopeService.isAdminOfCourse(2L), "but they do not administer it");
    }

    @Test
    void aCourseTheyAreOnlyEnrolledInAsAStudentIsNotOneTheyAdminister() {
        Student asStudent = new Student();
        asStudent.setId(77L);
        asStudent.setEmail("lecturer@example.com");
        Course studentCourse = new Course();
        studentCourse.setId(5L);
        StudentCourseAssignment enrolment = new StudentCourseAssignment(null, asStudent, studentCourse);
        when(adminCourseAssignmentRepository.findByLecturerId(10L)).thenReturn(Collections.emptyList());
        when(studentRepository.findAllByEmailIgnoreCase("lecturer@example.com")).thenReturn(List.of(asStudent));
        when(studentCourseAssignmentRepository.findByStudentId(77L)).thenReturn(List.of(enrolment));

        assertTrue(courseScopeService.getManagedCourseIdsForCurrentUser().contains(5L));
        assertFalse(courseScopeService.isAdminOfCourse(5L));
    }

    // --- The admin area only ever works on a course the person administers --------------------------------------------

    private AdminCourseAssignment assessorOnly(Course c) {
        return new AdminCourseAssignment(5L, lecturer, c);
    }

    @Test
    void adminPagesMoveAnAssessorOnlyCourseBackToTheAdminsOwnCourse() {
        when(adminCourseAssignmentRepository.findByLecturerId(10L)).thenReturn(List.of(assessorOnly(course2)));
        courseScopeService.setActiveCourseIdForCurrentUser(2L);   // they were working as an assessor in course 2
        assertEquals(2L, courseScopeService.getActiveCourseIdForCurrentUser());

        Long active = courseScopeService.ensureAdminActiveCourse();

        assertEquals(1L, active);                                  // their own course
        assertEquals(1L, courseScopeService.getActiveCourseIdForCurrentUser());
    }

    @Test
    void adminPagesLeaveAnAdminCourseAsIs() {
        AdminCourseAssignment invited = new AdminCourseAssignment(6L, lecturer, course2, Boolean.TRUE);
        when(adminCourseAssignmentRepository.findByLecturerId(10L)).thenReturn(List.of(invited));
        courseScopeService.setActiveCourseIdForCurrentUser(2L);

        assertEquals(2L, courseScopeService.ensureAdminActiveCourse());
    }

    @Test
    void theAdminAreaOffersOnlyTheCoursesThePersonAdministers() {
        when(adminCourseAssignmentRepository.findByLecturerId(10L)).thenReturn(List.of(assessorOnly(course2)));

        List<Course> offered = courseScopeService.getAdminAreaCoursesForCurrentUser();

        assertEquals(List.of(1L), offered.stream().map(Course::getId).toList());
        assertTrue(courseScopeService.getManagedCoursesForCurrentUser().stream().anyMatch(c -> c.getId().equals(2L)),
                "they can still work in course 2 from the assessor side");
        assertFalse(courseScopeService.canActAsAdminInCourse(2L));
        assertTrue(courseScopeService.canActAsAdminInCourse(1L));
    }
}
