package com.capstone.adproject.controller;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.LecturerRubricAssignmentRepository;
import com.capstone.adproject.repositories.LecturerStudentAssignmentRepository;
import com.capstone.adproject.repositories.StudentAssessmentAssignmentRepository;
import com.capstone.adproject.service.AdminService;
import com.capstone.adproject.service.AssessmentCommentService;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.AssessmentSubmissionService;
import com.capstone.adproject.service.CourseScopeService;
import com.capstone.adproject.service.CustomUserDetailsService;
import com.capstone.adproject.service.DeadlineService;
import com.capstone.adproject.service.EmailService;
import com.capstone.adproject.service.ProgressTrackingService;
import com.capstone.adproject.service.RubricService;
import com.capstone.adproject.service.SuperAdminService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
class AdminManagementControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AdminService adminService;
    @Mock
    private AssessmentService assessmentService;
    @Mock
    private DeadlineService deadlineService;
    @Mock
    private RubricService rubricService;
    @Mock
    private SuperAdminService superAdminService;
    @Mock
    private CourseScopeService courseScopeService;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private LecturerRepository lecturerRepository;
    @Mock
    private LecturerGroupAssignmentRepository assignmentRepository;
    @Mock
    private LecturerStudentAssignmentRepository studentAssignmentRepository;
    @Mock
    private LecturerRubricAssignmentRepository rubricAssignmentRepository;
    @Mock
    private StudentAssessmentAssignmentRepository studentAssessmentAssignmentRepository;
    @Mock
    private ProgressTrackingService progressTrackingService;
    @Mock
    private CustomUserDetailsService userDetailsService;
    @Mock
    private EmailService emailService;
    @Mock
    private AssessmentCommentService assessmentCommentService;
    @Mock
    private AssessmentSubmissionService assessmentSubmissionService;

    @InjectMocks
    private AdminController adminController;

    private Course course;
    private Assessment assessment;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminController).build();

        Authentication auth = new UsernamePasswordAuthenticationToken(
                "admin@example.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        course = new Course();
        course.setId(10L);
        course.setCourseName("SWE Capstone");

        assessment = new Assessment();
        assessment.setId(100L);
        assessment.setTitle("Phase 1 Assessment");
        assessment.setCourse(course);
    }

    @Test
    void testAdminHome_LoadsDashboard() throws Exception {
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(10L);
        when(assessmentService.findAllAssessmentsWithRubricsByCourseId(10L)).thenReturn(List.of(assessment));
        when(deadlineService.getDeadlinesByCourseId(10L)).thenReturn(Collections.emptyList());
        when(courseScopeService.getActiveCourseForCurrentUser()).thenReturn(course);

        mockMvc.perform(get("/admin/home"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin_home"))
                .andExpect(model().attributeExists("allAssessments", "deadlines", "activeCourse"));
    }

    @Test
    void testProgressTracking_AssessmentView_LoadsProgress() throws Exception {
        when(assessmentService.getAssessmentById(100L)).thenReturn(Optional.of(assessment));
        when(courseScopeService.isActiveCourseId(10L)).thenReturn(true);
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(10L);
        when(progressTrackingService.getLecturerProgress(assessment)).thenReturn(Map.of());
        when(progressTrackingService.getStudentProgress(assessment)).thenReturn(Map.of());
        when(assessmentCommentService.getModerationStats(100L)).thenReturn(Map.of());
        when(assessmentCommentService.getCommentsForModeration(eq(100L), anyString(), anyString(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(groupRepository.findByCourseId(10L)).thenReturn(Collections.emptyList());
        when(assessmentSubmissionService.getSubmissionMappingOverview(assessment)).thenReturn(Map.of());

        mockMvc.perform(get("/admin/progress-tracking/100"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin_progress_tracking_view"))
                .andExpect(model().attributeExists("assessment", "lecturerProgress", "studentProgress", "adminUsername"));
    }

    @Test
    void testDeleteStudent_ValidId_DeletesAndRedirects() throws Exception {
        Student student = new Student();
        student.setId(55L);
        student.setCourse(course);

        when(adminService.findStudentById(55L)).thenReturn(Optional.of(student));
        when(adminService.isStudentInActiveCourse(student)).thenReturn(true);

        mockMvc.perform(post("/admin/delete-student/55"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/manage-students"))
                .andExpect(flash().attribute("successMessage", "Student deleted successfully!"));

        verify(adminService).deleteStudentById(55L);
    }

    @Test
    void testDeleteLecturer_ValidId_DeletesAndRedirects() throws Exception {
        Lecturer lecturer = new Lecturer();
        lecturer.setId(66L);
        lecturer.setCourse(course);

        when(adminService.findLecturerById(66L)).thenReturn(Optional.of(lecturer));
        when(courseScopeService.isActiveCourseId(10L)).thenReturn(true);

        mockMvc.perform(post("/admin/delete-lecturer/66"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/manage-lecturers"))
                .andExpect(flash().attribute("successMessage", "Lecturer deleted successfully!"));

        verify(adminService).deleteLecturerById(66L);
    }
}
