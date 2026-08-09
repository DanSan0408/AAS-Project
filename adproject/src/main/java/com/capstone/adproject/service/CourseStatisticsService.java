package com.capstone.adproject.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        this.studentRepository = studentRepository;
        this.lecturerRepository = lecturerRepository;
        this.assessmentRepository = assessmentRepository;
        this.groupRepository = groupRepository;
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
        
        // Count records for this course
        long studentCount = studentRepository.findAll().stream()
                .filter(s -> s.getCourse() != null && s.getCourse().getId().equals(course.getId()))
                .count();
        
        long lecturerCount = lecturerRepository.findAll().stream()
                .filter(l -> l.getCourse() != null && l.getCourse().getId().equals(course.getId()))
                .count();

        List<String> adminEmails = new ArrayList<>();
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
            adminEmails = lecturerRepository.findAll().stream()
                    .filter(l -> l.getCourse() != null && l.getCourse().getId().equals(course.getId()))
                    .filter(l -> l.getRoles() != null && l.getRoles().contains("ROLE_ADMIN"))
                    .map(l -> l.getEmail())
                    .filter(e -> e != null && !e.isBlank())
                    .collect(Collectors.toList());
        }
        
        long assessmentCount = assessmentRepository.findAll().stream()
                .filter(a -> a.getCourse() != null && a.getCourse().getId().equals(course.getId()))
                .count();
        
        long groupCount = groupRepository.findAll().stream()
                .filter(g -> g.getCourse() != null && g.getCourse().getId().equals(course.getId()))
                .count();
        
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
}
