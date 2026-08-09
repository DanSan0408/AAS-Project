package com.capstone.adproject.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.capstone.adproject.model.Course;
import com.capstone.adproject.repositories.AdminCourseAssignmentRepository;
import com.capstone.adproject.repositories.AdminRepository;
import com.capstone.adproject.repositories.CourseRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.SuperAdminRepository;

class SuperAdminServiceTest {

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private LecturerRepository lecturerRepository;
    @Mock
    private AdminRepository adminRepository;
    @Mock
    private SuperAdminRepository superAdminRepository;
    @Mock
    private AdminCourseAssignmentRepository adminCourseAssignmentRepository;
    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;
    @Mock
    private CourseStatisticsService courseStatisticsService;
    @Mock
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @InjectMocks
    private SuperAdminService superAdminService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSaveCourse_Success() {
        Course course = new Course();
        course.setCourseName("Software Engineering");
        course.setCourseCode("SE101");

        when(courseRepository.findByCourseCodeIgnoreCase("SE101")).thenReturn(Optional.empty());
        when(courseRepository.save(any(Course.class))).thenReturn(course);

        Course saved = superAdminService.saveCourse(course);

        assertEquals("Software Engineering", saved.getCourseName());
        assertEquals("SE101", saved.getCourseCode());
        verify(courseRepository).save(course);
    }

    @Test
    void testSaveCourse_DuplicateCodeThrowsException() {
        Course existingCourse = new Course();
        existingCourse.setId(1L);
        existingCourse.setCourseName("Existing Software Engineering");
        existingCourse.setCourseCode("SE101");

        Course newCourse = new Course();
        newCourse.setCourseName("New Software Engineering");
        newCourse.setCourseCode("SE101");

        when(courseRepository.findByCourseCodeIgnoreCase("SE101")).thenReturn(Optional.of(existingCourse));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            superAdminService.saveCourse(newCourse);
        });

        assertEquals("The course exists, and the course ID has already been used. Please assign a new course ID.", exception.getMessage());
        verify(courseRepository, never()).save(any(Course.class));
    }

    @Test
    void testSaveCourse_UpdateSuccess() {
        Course existingCourse = new Course();
        existingCourse.setId(1L);
        existingCourse.setCourseName("Software Engineering");
        existingCourse.setCourseCode("SE101");

        when(courseRepository.findByCourseCodeIgnoreCase("SE101")).thenReturn(Optional.of(existingCourse));
        when(courseRepository.save(any(Course.class))).thenReturn(existingCourse);

        Course saved = superAdminService.saveCourse(existingCourse);

        assertEquals(existingCourse, saved);
        verify(courseRepository).save(existingCourse);
    }
}
