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
import com.capstone.adproject.repositories.AdminCourseAssignmentRepository;
import com.capstone.adproject.repositories.CourseRepository;
import com.capstone.adproject.repositories.LecturerRepository;

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
}

