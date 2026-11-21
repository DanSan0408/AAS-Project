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
import static org.mockito.Mockito.verify;
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
                null,
                0,
                "OK",
                Collections.emptyList(),
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
        when(groupRepository.findByCourseId(10L)).thenReturn(List.of(group));

        AiTeamDynamicsDtos.AuditResponse mockAudit = new AiTeamDynamicsDtos.AuditResponse(
                20L,
                "Group Alpha",
                85,
                "HEALTHY_COLLABORATION",
                null,
                0,
                "OK",
                Collections.emptyList(),
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
        lecturer.setCourse(course);

        when(assessmentService.getAssessmentById(100L)).thenReturn(Optional.of(assessment));
        when(groupRepository.findById(20L)).thenReturn(Optional.of(group));
        when(lecturerRepository.findByEmail("lecturer@domain.com")).thenReturn(Optional.of(lecturer));
        when(lecturerGroupAssignmentRepository.findByLecturerAndAssessment(lecturer, assessment)).thenReturn(Collections.emptyList());
        when(lecturerRubricAssignmentRepository.existsByLecturerAndAssessment(lecturer, assessment)).thenReturn(false);

        mockMvc.perform(get("/lecturer/comments/view/assessment/100/group/20/ai-audit").principal(auth))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Unauthorized assessment access."));
    }

    @Test
    void testViewComments_Admin_CrossCourse_Redirects() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "admin@domain.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        when(assessmentService.getAssessmentById(100L)).thenReturn(Optional.of(assessment));
        when(groupRepository.findById(20L)).thenReturn(Optional.of(group));
        when(courseScopeService.getActiveCourseIdsForCurrentUser()).thenReturn(Set.of(99L));

        mockMvc.perform(get("/admin/comments/view/assessment/100/group/20").principal(auth))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void testSelectGroup_Admin_ExcludesGroupsFromOtherCourses() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "admin@domain.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        Course otherCourse = new Course();
        otherCourse.setId(20L);
        otherCourse.setCourseName("Other Course");

        Group otherGroup = new Group();
        otherGroup.setId(99L);
        otherGroup.setGroupName("Cross-Course Beta");
        otherGroup.setCourse(otherCourse);
        otherGroup.setStudents(Collections.emptyList());

        when(assessmentService.getAssessmentById(100L)).thenReturn(Optional.of(assessment));
        when(courseScopeService.getActiveCourseIdsForCurrentUser()).thenReturn(Set.of(10L));
        when(groupRepository.findByCourseId(10L)).thenReturn(List.of(group));

        mockMvc.perform(get("/admin/comments/view/assessment/100/groups").principal(auth))
                .andExpect(status().isOk())
                .andExpect(model().attribute("groups", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void testSelectAssessment_MultiCourseLecturer_OnlyShowsActiveCourseAssessments() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "multi@domain.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_LECTURER")));

        Course otherCourse = new Course();
        otherCourse.setId(20L);
        otherCourse.setCourseName("Course 20");

        Assessment otherAssessment = new Assessment();
        otherAssessment.setId(200L);
        otherAssessment.setTitle("Course 20 Exam");
        otherAssessment.setCourse(otherCourse);

        Lecturer lecturerCourse10 = new Lecturer();
        lecturerCourse10.setId(5L);
        lecturerCourse10.setEmail("multi@domain.com");
        lecturerCourse10.setCourse(course);

        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(10L);
        when(lecturerRepository.findAllByEmailIgnoreCase("multi@domain.com")).thenReturn(List.of(lecturerCourse10));
        when(lecturerGroupAssignmentRepository.findAssessmentsByLecturer(lecturerCourse10))
                .thenReturn(List.of(assessment, otherAssessment));
        when(lecturerRubricAssignmentRepository.findAssessmentsByLecturer(lecturerCourse10))
                .thenReturn(Collections.emptyList());
        when(groupRepository.findByCourseId(10L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/lecturer/comments/view/assessments").principal(auth))
                .andExpect(status().isOk())
                .andExpect(view().name("lecturer_comment_select_assessment"))
                .andExpect(model().attribute("assessments", org.hamcrest.Matchers.hasSize(1)));
    }

    // --- What the AI audit is sent, who may run it, and what a failure shows ----------------------------------------

    private com.capstone.adproject.model.Student student(long id, String name) {
        com.capstone.adproject.model.Student s = new com.capstone.adproject.model.Student();
        s.setId(id);
        s.setUsername(name);
        s.setEmail(name.toLowerCase() + "@uni.edu.my");
        s.setGroup(group);
        s.setCourse(course);
        return s;
    }

    private com.capstone.adproject.model.Mark ratingMark(com.capstone.adproject.model.Student by, com.capstone.adproject.model.Student about,
                                                         double value, com.capstone.adproject.model.Mark.SubmissionStatus status, Lecturer assessor) {
        com.capstone.adproject.model.Mark m = new com.capstone.adproject.model.Mark();
        m.setEvaluatorStudent(by);
        m.setEvaluatedStudent(about);
        m.setAssessment(assessment);
        m.setMarkValue(value);
        m.setStatus(status);
        m.setLecturer(assessor);
        return m;
    }

    private AiTeamDynamicsDtos.AuditResponse anyAudit() {
        return new AiTeamDynamicsDtos.AuditResponse(20L, "Group Alpha", 90, "HEALTHY_COLLABORATION", null, 0, "OK", List.of(),
                "ok", List.of(), List.of(), List.of());
    }

    @Test
    void testAiAudit_SendsOnlyFinishedStudentRatings_NotAssessorMarksOrDrafts() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken("admin@domain.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        com.capstone.adproject.model.Student ali = student(1, "Ali");
        com.capstone.adproject.model.Student bella = student(2, "Bella");
        group.setStudents(new java.util.ArrayList<>(List.of(ali, bella)));
        Lecturer drLim = new Lecturer();
        drLim.setId(5L);
        when(assessmentService.getAssessmentById(100L)).thenReturn(Optional.of(assessment));
        when(groupRepository.findById(20L)).thenReturn(Optional.of(group));
        when(courseScopeService.getActiveCourseIdsForCurrentUser()).thenReturn(Set.of(10L));
        when(markRepository.findByGroupMembersAndAssessment(any(), eq(assessment))).thenReturn(List.of(
                ratingMark(ali, bella, 4, com.capstone.adproject.model.Mark.SubmissionStatus.FINAL, null),             // a real peer rating
                ratingMark(bella, bella, 5, com.capstone.adproject.model.Mark.SubmissionStatus.FINAL, drLim),          // an assessor's mark, stored as "self"
                ratingMark(bella, ali, 3, com.capstone.adproject.model.Mark.SubmissionStatus.DRAFT, null)));          // not submitted yet
        when(aiServiceClient.auditTeamDynamics(any(AiTeamDynamicsDtos.AuditRequest.class))).thenReturn(anyAudit());

        mockMvc.perform(get("/admin/comments/view/assessment/100/group/20/ai-audit").principal(auth)).andExpect(status().isOk());

        org.mockito.ArgumentCaptor<AiTeamDynamicsDtos.AuditRequest> sent = org.mockito.ArgumentCaptor.forClass(AiTeamDynamicsDtos.AuditRequest.class);
        verify(aiServiceClient).auditTeamDynamics(sent.capture());
        org.junit.jupiter.api.Assertions.assertEquals(1, sent.getValue().peerRatings().size());
        org.junit.jupiter.api.Assertions.assertEquals(4.0, sent.getValue().peerRatings().get(0).earned());
    }

    @Test
    void testAiAudit_SendsOnlyApprovedTeammateCommentsWithoutTheWritersName() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken("admin@domain.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        com.capstone.adproject.model.Student ali = student(1, "Ali");
        group.setStudents(new java.util.ArrayList<>(List.of(ali)));
        com.capstone.adproject.model.AssessmentComment approved = new com.capstone.adproject.model.AssessmentComment();
        approved.setCommentText("Always on time.");
        approved.setAssessment(assessment);
        when(assessmentService.getAssessmentById(100L)).thenReturn(Optional.of(assessment));
        when(groupRepository.findById(20L)).thenReturn(Optional.of(group));
        when(courseScopeService.getActiveCourseIdsForCurrentUser()).thenReturn(Set.of(10L));
        when(calculateService.getApprovedPeerComments(ali, assessment)).thenReturn(List.of(approved));
        when(aiServiceClient.auditTeamDynamics(any(AiTeamDynamicsDtos.AuditRequest.class))).thenReturn(anyAudit());

        mockMvc.perform(get("/admin/comments/view/assessment/100/group/20/ai-audit").principal(auth)).andExpect(status().isOk());

        org.mockito.ArgumentCaptor<AiTeamDynamicsDtos.AuditRequest> sent = org.mockito.ArgumentCaptor.forClass(AiTeamDynamicsDtos.AuditRequest.class);
        verify(aiServiceClient).auditTeamDynamics(sent.capture());
        org.junit.jupiter.api.Assertions.assertEquals(1, sent.getValue().peerComments().size());
        org.junit.jupiter.api.Assertions.assertEquals("Teammate", sent.getValue().peerComments().get(0).evaluatorName());
        verify(calculateService, org.mockito.Mockito.never()).getAllAssessmentComments(any(), any());
    }

    @Test
    void testAiAudit_Failure_ShowsAGenericMessageNotTheInternalError() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken("admin@domain.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(assessmentService.getAssessmentById(100L)).thenReturn(Optional.of(assessment));
        when(groupRepository.findById(20L)).thenReturn(Optional.of(group));
        when(courseScopeService.getActiveCourseIdsForCurrentUser()).thenReturn(Set.of(10L));
        when(aiServiceClient.auditTeamDynamics(any(AiTeamDynamicsDtos.AuditRequest.class)))
                .thenThrow(new RuntimeException("500 from http://ai-service:8000 detail=db password"));

        mockMvc.perform(get("/admin/comments/view/assessment/100/group/20/ai-audit").principal(auth))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("The AI analysis is unavailable right now. Try again in a few minutes."));
    }

    @Test
    void testAiAudit_AnAssessorWhoDoesNotSuperviseTheGroupIsRefused() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken("lecturer@domain.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_LECTURER")));
        Lecturer lecturer = new Lecturer();
        lecturer.setId(5L);
        lecturer.setEmail("lecturer@domain.com");
        lecturer.setCourse(course);
        Lecturer someoneElse = new Lecturer();
        someoneElse.setId(6L);
        group.setAcademicSupervisor(someoneElse);
        when(assessmentService.getAssessmentById(100L)).thenReturn(Optional.of(assessment));
        when(groupRepository.findById(20L)).thenReturn(Optional.of(group));
        when(lecturerRepository.findByEmail("lecturer@domain.com")).thenReturn(Optional.of(lecturer));
        when(lecturerGroupAssignmentRepository.findByLecturerAndAssessment(lecturer, assessment)).thenReturn(Collections.emptyList());
        when(lecturerRubricAssignmentRepository.existsByLecturerAndAssessment(lecturer, assessment)).thenReturn(false);

        mockMvc.perform(get("/lecturer/comments/view/assessment/100/group/20/ai-audit").principal(auth))
                .andExpect(status().isForbidden());

        verify(aiServiceClient, org.mockito.Mockito.never()).auditTeamDynamics(any());
    }
}
