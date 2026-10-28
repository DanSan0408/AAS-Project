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

import com.capstone.adproject.dto.ai.AiTeamDynamicsDtos;
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
import com.capstone.adproject.repositories.MarkRepository;
import com.capstone.adproject.service.AiServiceClient;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.CalculateService;
import com.capstone.adproject.service.CourseScopeService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    @Mock
    private MarkRepository markRepository;
    @Mock
    private AiServiceClient aiServiceClient;

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

    @Test
    void testAiAuditTeamDynamics_Admin_Success() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "admin@domain.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        when(assessmentService.getAssessmentById(100L)).thenReturn(Optional.of(assessment));
        when(groupRepository.findById(20L)).thenReturn(Optional.of(group));
        when(courseScopeService.getActiveCourseIdsForCurrentUser()).thenReturn(Set.of(10L));

        AiTeamDynamicsDtos.AuditResponse mockAudit = new AiTeamDynamicsDtos.AuditResponse(
                20L,
                "Group Alpha",
                88,
                "HEALTHY",
                "Team is performing well with high mutual cohesion.",
                Collections.emptyList(),
                Collections.emptyList(),
                List.of("Continue standard monitoring")
        );

        when(aiServiceClient.auditTeamDynamics(any(AiTeamDynamicsDtos.AuditRequest.class))).thenReturn(mockAudit);

        mockMvc.perform(get("/admin/comments/view/assessment/100/group/20/ai-audit").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(20L))
                .andExpect(jsonPath("$.groupName").value("Group Alpha"))
                .andExpect(jsonPath("$.teamHealthScore").value(88))
                .andExpect(jsonPath("$.collaborationStatus").value("HEALTHY"))
                .andExpect(jsonPath("$.executiveSummary").value("Team is performing well with high mutual cohesion."));
    }

    @Test
    void testAiAuditTeamDynamics_NotFound_Returns404() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "admin@domain.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        when(assessmentService.getAssessmentById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/admin/comments/view/assessment/999/group/20/ai-audit").principal(auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Assessment or Group not found."));
    }

    @Test
    void testAiAuditTeamDynamics_Lecturer_Success() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "lecturer@domain.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_LECTURER")));

        Lecturer lecturer = new Lecturer();
        lecturer.setId(5L);
        lecturer.setEmail("lecturer@domain.com");
        lecturer.setCourse(course);

        group.setAcademicSupervisor(lecturer);

        when(assessmentService.getAssessmentById(100L)).thenReturn(Optional.of(assessment));
        when(groupRepository.findById(20L)).thenReturn(Optional.of(group));
        when(lecturerRepository.findByEmail("lecturer@domain.com")).thenReturn(Optional.of(lecturer));
        when(lecturerGroupAssignmentRepository.findByLecturerAndAssessment(lecturer, assessment)).thenReturn(Collections.emptyList());
        when(lecturerRubricAssignmentRepository.existsByLecturerAndAssessment(lecturer, assessment)).thenReturn(false);
        when(groupRepository.findAll()).thenReturn(List.of(group));

        AiTeamDynamicsDtos.AuditResponse mockAudit = new AiTeamDynamicsDtos.AuditResponse(
                20L,
                "Group Alpha",
                85,
                "HEALTHY_COLLABORATION",
                "Good team collaboration.",
                Collections.emptyList(),
                Collections.emptyList(),
                List.of("Continue standard monitoring")
        );

        when(aiServiceClient.auditTeamDynamics(any(AiTeamDynamicsDtos.AuditRequest.class))).thenReturn(mockAudit);

        mockMvc.perform(get("/lecturer/comments/view/assessment/100/group/20/ai-audit").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(20L))
                .andExpect(jsonPath("$.teamHealthScore").value(85))
                .andExpect(jsonPath("$.collaborationStatus").value("HEALTHY_COLLABORATION"));
    }

    @Test
    void testAiAuditTeamDynamics_Lecturer_UnauthorizedWhenNotAssigned() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "lecturer@domain.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_LECTURER")));

        Lecturer lecturer = new Lecturer();
        lecturer.setId(5L);
        lecturer.setEmail("lecturer@domain.com");

        when(assessmentService.getAssessmentById(100L)).thenReturn(Optional.of(assessment));
        when(groupRepository.findById(20L)).thenReturn(Optional.of(group));
        when(lecturerRepository.findByEmail("lecturer@domain.com")).thenReturn(Optional.of(lecturer));
        when(lecturerGroupAssignmentRepository.findByLecturerAndAssessment(lecturer, assessment)).thenReturn(Collections.emptyList());
        when(lecturerRubricAssignmentRepository.existsByLecturerAndAssessment(lecturer, assessment)).thenReturn(false);
        when(groupRepository.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/lecturer/comments/view/assessment/100/group/20/ai-audit").principal(auth))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Unauthorized assessment access."));
    }
}
