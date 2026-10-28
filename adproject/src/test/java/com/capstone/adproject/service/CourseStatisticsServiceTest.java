package com.capstone.adproject.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.repositories.AssessmentRepository;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.StudentRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@ExtendWith(MockitoExtension.class)
class CourseStatisticsServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private LecturerRepository lecturerRepository;

    @Mock
    private AssessmentRepository assessmentRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query nativeQuery;

    @InjectMocks
    private CourseStatisticsService courseStatisticsService;

    private Course testCourse;

    @BeforeEach
    void setUp() {
        testCourse = new Course();
        testCourse.setId(42L);
        testCourse.setCourseName("Distributed Systems");
        testCourse.setCourseCode("SECJ3104");
        testCourse.setDescription("Advanced systems course");
    }

    @Test
    void testGetCourseStatistics_ReturnsEmptyWhenCourseNull() {
        Map<String, Object> stats = courseStatisticsService.getCourseStatistics(null);
        assertTrue(stats.isEmpty());
        verify(studentRepository, never()).countByCourseId(any());
    }

    @Test
    void testGetCourseStatistics_UsesDatabaseAggregationQueries() {
        when(studentRepository.countByCourseId(42L)).thenReturn(85L);
        when(lecturerRepository.countByCourseId(42L)).thenReturn(6L);
        when(assessmentRepository.countByCourseId(42L)).thenReturn(4L);
        when(groupRepository.countByCourseId(42L)).thenReturn(17L);

        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter("courseId", 42L)).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of("admin1@utm.my", "admin2@utm.my"));

        Map<String, Object> stats = courseStatisticsService.getCourseStatistics(testCourse);

        assertEquals("Distributed Systems", stats.get("courseName"));
        assertEquals("SECJ3104", stats.get("courseCode"));
        assertEquals("Advanced systems course", stats.get("description"));
        assertEquals(85L, stats.get("studentCount"));
        assertEquals(6L, stats.get("lecturerCount"));
        assertEquals(4L, stats.get("assessmentCount"));
        assertEquals(17L, stats.get("groupCount"));
        assertEquals(2, stats.get("adminCount"));

        // Verify database aggregation queries were called
        verify(studentRepository).countByCourseId(42L);
        verify(lecturerRepository).countByCourseId(42L);
        verify(assessmentRepository).countByCourseId(42L);
        verify(groupRepository).countByCourseId(42L);

        // Verify full table scans were NOT performed
        verify(studentRepository, never()).findAll();
        verify(lecturerRepository, never()).findAll();
        verify(assessmentRepository, never()).findAll();
        verify(groupRepository, never()).findAll();
    }

    @Test
    void testGetCourseStatistics_FallbackUsesScopedCourseLecturers() {
        when(studentRepository.countByCourseId(42L)).thenReturn(10L);
        when(lecturerRepository.countByCourseId(42L)).thenReturn(2L);
        when(assessmentRepository.countByCourseId(42L)).thenReturn(1L);
        when(groupRepository.countByCourseId(42L)).thenReturn(3L);

        // Force exception in native query to trigger fallback
        when(entityManager.createNativeQuery(anyString())).thenThrow(new RuntimeException("Native query error"));

        Lecturer adminLecturer = new Lecturer();
        adminLecturer.setEmail("fallback.admin@utm.my");
        adminLecturer.setRoles("ROLE_LECTURER,ROLE_ADMIN");

        when(lecturerRepository.findByCourseId(42L)).thenReturn(Collections.singletonList(adminLecturer));

        Map<String, Object> stats = courseStatisticsService.getCourseStatistics(testCourse);

        assertEquals(1, stats.get("adminCount"));
        assertEquals(List.of("fallback.admin@utm.my"), stats.get("adminEmails"));

        // Verify fallback used findByCourseId instead of findAll()
        verify(lecturerRepository).findByCourseId(42L);
        verify(lecturerRepository, never()).findAll();
    }
}
