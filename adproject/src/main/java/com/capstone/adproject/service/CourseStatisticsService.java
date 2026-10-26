package com.capstone.adproject.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.capstone.adproject.model.Course;
import com.capstone.adproject.repositories.AssessmentRepository;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.StudentRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class CourseStatisticsService {

    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;
    private final AssessmentRepository assessmentRepository;
    private final GroupRepository groupRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public CourseStatisticsService(
            StudentRepository studentRepository,
            LecturerRepository lecturerRepository,
            AssessmentRepository assessmentRepository,
            GroupRepository groupRepository) {
        this(studentRepository, lecturerRepository, assessmentRepository, groupRepository, null);
    }

    @Autowired
    public CourseStatisticsService(
            StudentRepository studentRepository,
            LecturerRepository lecturerRepository,
            AssessmentRepository assessmentRepository,
            GroupRepository groupRepository,
            @Autowired(required = false) EntityManager entityManager) {
        this.studentRepository = studentRepository;
        this.lecturerRepository = lecturerRepository;
        this.assessmentRepository = assessmentRepository;
        this.groupRepository = groupRepository;
        this.entityManager = entityManager;
    }

    /**
     * Get statistics for a specific course
     * @param course The course to get statistics for
     * @return Map containing course statistics
     */
    public Map<String, Object> getCourseStatistics(Course course) {
        Map<String, Object> stats = new HashMap<>();
        
        if (course == null) {
            return stats;
        }
        
        // Count records for this course using database aggregation queries
        long studentCount = studentRepository.countByCourseId(course.getId());
        long lecturerCount = lecturerRepository.countByCourseId(course.getId());

        List<String> adminEmails = new ArrayList<>();
        if (entityManager != null) {
            try {
                @SuppressWarnings("unchecked")
                List<String> mappedAdminEmails = entityManager.createNativeQuery(
                        "SELECT DISTINCT l.email "
                        + "FROM admin_course_assignment aca "
                        + "JOIN lecturer l ON l.id = aca.lecturer_id "
                        + "WHERE aca.course_id = :courseId "
                        + "AND l.roles LIKE '%ROLE_ADMIN%'")
                        .setParameter("courseId", course.getId())
                        .getResultList();

                adminEmails = mappedAdminEmails.stream()
                        .filter(e -> e != null && !e.isBlank())
                        .collect(Collectors.toList());
            } catch (Exception ex) {
                adminEmails = getAdminEmailsFallback(course.getId());
            }
        } else {
            adminEmails = getAdminEmailsFallback(course.getId());
        }
        
        long assessmentCount = assessmentRepository.countByCourseId(course.getId());
        long groupCount = groupRepository.countByCourseId(course.getId());
        
        stats.put("courseName", course.getCourseName());
        stats.put("courseCode", course.getCourseCode());
        stats.put("description", course.getDescription());
        stats.put("studentCount", studentCount);
        stats.put("lecturerCount", lecturerCount);
        stats.put("adminCount", adminEmails.size());
        stats.put("adminEmails", adminEmails);
        stats.put("assessmentCount", assessmentCount);
        stats.put("groupCount", groupCount);
        
        return stats;
    }

    private List<String> getAdminEmailsFallback(Long courseId) {
        return lecturerRepository.findByCourseId(courseId).stream()
                .filter(l -> l.getRoles() != null && l.getRoles().contains("ROLE_ADMIN"))
                .map(l -> l.getEmail())
                .filter(e -> e != null && !e.isBlank())
                .collect(Collectors.toList());
    }
}
