package com.capstone.adproject.controller;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.capstone.adproject.model.AdminCourseAssignment;
import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.repositories.AdminCourseAssignmentRepository;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.LecturerRubricAssignmentRepository;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.CalculateService;
import com.capstone.adproject.service.CourseScopeService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
class GroupCommentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AssessmentService assessmentService;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private LecturerRepository lecturerRepository;
    @Mock
    private LecturerGroupAssignmentRepository lecturerGroupAssignmentRepository;
    @Mock
    private LecturerRubricAssignmentRepository lecturerRubricAssignmentRepository;
    @Mock
    private AdminCourseAssignmentRepository adminCourseAssignmentRepository;
    @Mock
    private CalculateService calculateService;
    @Mock
    private CourseScopeService courseScopeService;

    @InjectMocks
    private GroupCommentController groupCommentController;

    private Course course;
    private Assessment assessment;
    private Group group;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(groupCommentController).build();

        course = new Course();
        course.setId(10L);
        course.setCourseName("SWE Capstone");

        assessment = new Assessment();
        assessment.setId(100L);
        assessment.setTitle("Sprint 1");
        assessment.setCourse(course);

        group = new Group();
        group.setId(20L);
        group.setGroupName("Group Alpha");
        group.setCourse(course);
        group.setStudents(new java.util.ArrayList<>());
    }

    @Test
    void testSelectAssessment_Admin_RedirectsToProgressTrackingComments() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "admin@domain.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        mockMvc.perform(get("/admin/comments/view/assessments").principal(auth))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/progress-tracking?tab=comments"));
    }

    @Test
    void testViewComments_Admin_LoadsCommentsForGroup() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "admin@domain.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        when(assessmentService.getAssessmentById(100L)).thenReturn(Optional.of(assessment));
        when(groupRepository.findById(20L)).thenReturn(Optional.of(group));
        when(courseScopeService.getActiveCourseIdsForCurrentUser()).thenReturn(Set.of(10L));

        mockMvc.perform(get("/admin/comments/view/assessment/100/group/20").principal(auth))
                .andExpect(status().isOk())
                .andExpect(view().name("comment_view"))
                .andExpect(model().attributeExists("assessment", "group", "students", "allCommentsMap"));
    }
}
