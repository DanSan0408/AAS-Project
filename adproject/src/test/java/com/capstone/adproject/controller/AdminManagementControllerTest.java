package com.capstone.adproject.controller;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
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
import com.capstone.adproject.model.AssessmentComment;
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
import static org.mockito.Mockito.never;
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

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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

    @Test
    void testManageCourses_LoadsPageWithCourses() throws Exception {
        when(courseScopeService.getManagedCoursesForCurrentUser()).thenReturn(List.of(course));
        when(superAdminService.getAllAdminLecturers()).thenReturn(Collections.emptyList());
        when(superAdminService.getAdminManagedCoursesMap(any())).thenReturn(Collections.emptyMap());

        mockMvc.perform(get("/admin/manage-courses"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin_manage_courses"))
                .andExpect(model().attributeExists("courses", "creatorOwnedCourses", "newCourse", "adminUsername"));
    }

    @Test
    void testAddCourse_ValidCourse_SavesAndRedirects() throws Exception {
        Lecturer adminLecturer = new Lecturer();
        adminLecturer.setId(5L);
        adminLecturer.setEmail("admin@example.com");

        Course savedCourse = new Course();
        savedCourse.setId(15L);
        savedCourse.setCourseName("New Course");
        savedCourse.setCourseCode("SECJ101");

        when(superAdminService.resolveLecturerByIdentity("admin@example.com"))
                .thenReturn(Optional.of(adminLecturer));
        when(superAdminService.saveCourse(any(Course.class))).thenReturn(savedCourse);

        mockMvc.perform(post("/admin/courses/add")
                .param("courseName", "New Course")
                .param("courseCode", "SECJ101")
                .param("description", "Intro course"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/manage-courses"))
                .andExpect(flash().attribute("successMessage", "Course added successfully!"));

        verify(superAdminService).assignAdminToCourse(5L, 15L);
    }

    @Test
    void testAddCourse_ValidationError_RedirectsWithErrors() throws Exception {
        mockMvc.perform(post("/admin/courses/add")
                .param("courseName", "")
                .param("courseCode", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/manage-courses"))
                .andExpect(flash().attribute("errorMessage", "Validation failed. Please check the course fields."));
    }

    @Test
    void testAddCourse_ServiceThrowsException_RedirectsWithErrorMessage() throws Exception {
        Lecturer adminLecturer = new Lecturer();
        adminLecturer.setId(5L);
        adminLecturer.setEmail("admin@example.com");

        when(superAdminService.resolveLecturerByIdentity("admin@example.com"))
                .thenReturn(Optional.of(adminLecturer));
        when(superAdminService.saveCourse(any(Course.class)))
                .thenThrow(new RuntimeException("Field 'course_code' doesn't have a default value"));

        mockMvc.perform(post("/admin/courses/add")
                .param("courseName", "New Course")
                .param("courseCode", "SECJ101"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/manage-courses"))
                .andExpect(flash().attribute("errorMessage",
                        "Error adding course: Field 'course_code' doesn't have a default value"));
    }

    @Test
    void testEditCourse_Authorized_LoadsEditPage() throws Exception {
        when(superAdminService.getCourseById(10L)).thenReturn(Optional.of(course));
        when(courseScopeService.isAdminOfCourse(10L)).thenReturn(true);

        mockMvc.perform(get("/admin/courses/edit/10"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin_edit_course"))
                .andExpect(model().attributeExists("course", "adminUsername"));
    }

    @Test
    void testEditCourse_Unauthorized_Redirects() throws Exception {
        when(superAdminService.getCourseById(999L)).thenReturn(Optional.of(course));
        when(courseScopeService.isAdminOfCourse(10L)).thenReturn(false);

        mockMvc.perform(get("/admin/courses/edit/999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/manage-courses"))
                .andExpect(flash().attribute("errorMessage", "You are not authorized to edit this course."));
    }

    @Test
    void testEditCourse_NotFound_Redirects() throws Exception {
        when(superAdminService.getCourseById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/admin/courses/edit/999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/manage-courses"))
                .andExpect(flash().attribute("errorMessage", "Course not found."));
    }

    @Test
    void testUpdateCourse_ValidCourse_UpdatesAndRedirects() throws Exception {
        when(superAdminService.getCourseById(10L)).thenReturn(Optional.of(course));
        when(courseScopeService.isAdminOfCourse(10L)).thenReturn(true);
        when(superAdminService.saveCourse(any(Course.class))).thenReturn(course);

        mockMvc.perform(post("/admin/courses/update")
                .param("id", "10")
                .param("courseName", "Updated Course")
                .param("courseCode", "SECJ101"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/manage-courses"))
                .andExpect(flash().attribute("successMessage", "Course updated successfully!"));

        verify(superAdminService).saveCourse(any(Course.class));
    }

    @Test
    void testUpdateCourse_Unauthorized_Redirects() throws Exception {
        when(superAdminService.getCourseById(10L)).thenReturn(Optional.of(course));
        when(courseScopeService.isAdminOfCourse(10L)).thenReturn(false);

        mockMvc.perform(post("/admin/courses/update")
                .param("id", "10")
                .param("courseName", "Updated Course")
                .param("courseCode", "SECJ101"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/manage-courses"))
                .andExpect(flash().attribute("errorMessage", "You are not authorized to update this course."));
    }

    @Test
    void testDeleteCourse_ValidCourse_DeletesAndRedirects() throws Exception {
        when(superAdminService.getCourseById(10L)).thenReturn(Optional.of(course));
        when(courseScopeService.isAdminOfCourse(10L)).thenReturn(true);

        mockMvc.perform(post("/admin/courses/delete/10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/manage-courses"))
                .andExpect(flash().attribute("successMessage", "Course deleted successfully!"));

        verify(superAdminService).deleteCourse(10L);
    }

    @Test
    void testDeleteCourse_Unauthorized_Redirects() throws Exception {
        when(superAdminService.getCourseById(10L)).thenReturn(Optional.of(course));
        when(courseScopeService.isAdminOfCourse(10L)).thenReturn(false);

        mockMvc.perform(post("/admin/courses/delete/10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/manage-courses"))
                .andExpect(flash().attribute("errorMessage", "You are not authorized to delete this course."));
    }

    @Test
    void testCheckCourseCode_Exists_ReturnsTrue() throws Exception {
        when(superAdminService.getCourseByCode("SECJ101")).thenReturn(Optional.of(course));

        mockMvc.perform(get("/admin/courses/check-code")
                .param("courseCode", "SECJ101"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String json = result.getResponse().getContentAsString();
                    org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"exists\":true"));
                });
    }

    @Test
    void testCheckCourseCode_NotExists_ReturnsFalse() throws Exception {
        when(superAdminService.getCourseByCode("UNKNOWN")).thenReturn(Optional.empty());

        mockMvc.perform(get("/admin/courses/check-code")
                .param("courseCode", "UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String json = result.getResponse().getContentAsString();
                    org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"exists\":false"));
                });
    }

    @Test
    void testSwitchCourse_FromResourceUrl_RedirectsToSectionRoot() throws Exception {
        when(courseScopeService.canActAsAdminInCourse(10L)).thenReturn(true);
        when(courseScopeService.setActiveCourseIdForCurrentUser(10L)).thenReturn(true);

        mockMvc.perform(post("/admin/switch-course")
                .param("courseId", "10")
                .header("Referer", "http://localhost:8080/rubrics/view/5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rubrics/manage"));
    }

    @Test
    void testApproveComment_CommentFromOtherAssessment_IsRejected() throws Exception {
        Assessment activeAssessment = new Assessment();
        activeAssessment.setId(50L);
        activeAssessment.setCourse(course);

        Course otherCourse = new Course();
        otherCourse.setId(99L);
        Assessment otherAssessment = new Assessment();
        otherAssessment.setId(60L);
        otherAssessment.setCourse(otherCourse);

        AssessmentComment foreignComment = new AssessmentComment();
        foreignComment.setId(700L);
        foreignComment.setAssessment(otherAssessment);

        when(assessmentService.getAssessmentById(50L)).thenReturn(Optional.of(activeAssessment));
        when(courseScopeService.isActiveCourseId(10L)).thenReturn(true);
        when(assessmentCommentService.getCommentById(700L)).thenReturn(Optional.of(foreignComment));

        mockMvc.perform(post("/admin/comments/700/approve")
                .param("assessmentId", "50"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/progress-tracking"));

        verify(assessmentCommentService, never()).approveComment(eq(700L), anyString());
    }

    @Test
    void testCreateRandomGroups_CrossCourseSupervisor_IsRejected() throws Exception {
        Course otherCourse = new Course();
        otherCourse.setId(99L);
        Lecturer foreignLecturer = new Lecturer();
        foreignLecturer.setId(88L);
        foreignLecturer.setCourse(otherCourse);

        when(adminService.findLecturerById(88L)).thenReturn(Optional.of(foreignLecturer));
        when(courseScopeService.isActiveCourseId(99L)).thenReturn(false);

        mockMvc.perform(post("/admin/group-assignment/randomize/create")
                .param("groupName", "Random Team")
                .param("academicSupervisorId", "88"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/group-assignment"))
                .andExpect(flash().attributeExists("error"));

        verify(adminService, never()).createSingleGroupFromRandomization(any());
    }

    // --- Evaluations tab: pick first, then read or delete (never edit) -------------------------------------------

    private void allowAssessment100() {
        when(assessmentService.getAssessmentById(100L)).thenReturn(Optional.of(assessment));
        when(courseScopeService.isActiveCourseId(10L)).thenReturn(true);
    }

    private void stubTrackingPageBasics() {
        allowAssessment100();
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(10L);
        when(progressTrackingService.getLecturerProgress(assessment)).thenReturn(Map.of());
        when(progressTrackingService.getStudentProgress(assessment)).thenReturn(Map.of());
        when(assessmentCommentService.getModerationStats(100L)).thenReturn(Map.of());
        when(assessmentCommentService.getCommentsForModeration(eq(100L), anyString(), anyString(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(groupRepository.findByCourseId(10L)).thenReturn(Collections.emptyList());
        when(assessmentSubmissionService.getSubmissionMappingOverview(assessment)).thenReturn(Map.of());
        when(assessmentService.findAllAssessmentsWithRubricsByCourseId(10L)).thenReturn(List.of(assessment));
    }

    @Test
    void evaluationsTab_ShowsNothingUntilSomethingIsPicked() throws Exception {
        stubTrackingPageBasics();

        mockMvc.perform(get("/admin/progress-tracking/100").param("tab", "evaluations"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("evaluationsPicked", false))
                .andExpect(model().attribute("evaluations", Collections.emptyList()));

        verify(progressTrackingService, never()).findEvaluations(any(), any(), any(), any());
    }

    @Test
    void evaluationsTab_ListsEvaluationsForThePickedFilters() throws Exception {
        stubTrackingPageBasics();
        when(progressTrackingService.findEvaluations(assessment, 3L, null, 12L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/progress-tracking/100").param("tab", "evaluations")
                        .param("evalGroupId", "3").param("evalRubricId", "12"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("evaluationsPicked", true))
                .andExpect(model().attributeExists("courseAssessments", "evaluationStudents", "evaluationRubrics", "recentDeletions"));

        verify(progressTrackingService).findEvaluations(assessment, 3L, null, 12L);
    }

    @Test
    void evaluationsTab_ShowAllListsEveryEvaluationWithoutAnyFilter() throws Exception {
        stubTrackingPageBasics();
        when(progressTrackingService.findEvaluations(assessment, null, null, null)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/progress-tracking/100").param("tab", "evaluations").param("evalAll", "true"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("evaluationsPicked", true))
                .andExpect(model().attribute("evalAll", true));

        verify(progressTrackingService).findEvaluations(assessment, null, null, null);
    }

    @Test
    void evaluationsPicker_KeepsFiltersForTheSameAssessmentAndResetsThemWhenSwitching() throws Exception {
        mockMvc.perform(get("/admin/progress-tracking/evaluations")
                        .param("assessmentId", "100").param("currentAssessmentId", "100").param("evalStudentId", "9"))
                .andExpect(redirectedUrl("/admin/progress-tracking/100?tab=evaluations&evalStudentId=9"));

        mockMvc.perform(get("/admin/progress-tracking/evaluations")
                        .param("assessmentId", "200").param("currentAssessmentId", "100").param("evalStudentId", "9"))
                .andExpect(redirectedUrl("/admin/progress-tracking/200?tab=evaluations"));
    }

    @Test
    void deleteEvaluation_DeletesRecordsTheActorAndReturnsToTheSameFilters() throws Exception {
        allowAssessment100();
        when(progressTrackingService.deleteEvaluation(eq(assessment), eq("LECTURER"), eq(5L), eq(12L),
                eq("Individual Assessment"), eq(List.of(103L)), eq("Wrong student"), anyString())).thenReturn(4);

        mockMvc.perform(post("/admin/progress-tracking/100/evaluations/delete")
                        .param("evaluatorKind", "LECTURER").param("evaluatorId", "5").param("rubricId", "12")
                        .param("assessmentType", "Individual Assessment").param("targetIds", "103")
                        .param("reason", "Wrong student").param("evalStudentId", "103"))
                .andExpect(redirectedUrl("/admin/progress-tracking/100?tab=evaluations&evalStudentId=103"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void deleteEvaluation_ShowsTheServiceMessageWhenItIsRefused() throws Exception {
        allowAssessment100();
        when(progressTrackingService.deleteEvaluation(any(), anyString(), any(), any(), anyString(), any(), any(), anyString()))
                .thenThrow(new IllegalArgumentException("Give a reason for deleting this evaluation."));

        mockMvc.perform(post("/admin/progress-tracking/100/evaluations/delete")
                        .param("evaluatorKind", "STUDENT").param("evaluatorId", "1")
                        .param("assessmentType", "Peer Assessment").param("targetIds", "2"))
                .andExpect(flash().attribute("errorMessage", "Give a reason for deleting this evaluation."));
    }

    @Test
    void deleteEvaluation_RefusesAnAssessmentOutsideTheAdminsCourse() throws Exception {
        when(assessmentService.getAssessmentById(100L)).thenReturn(Optional.of(assessment));
        when(courseScopeService.isActiveCourseId(10L)).thenReturn(false);

        mockMvc.perform(post("/admin/progress-tracking/100/evaluations/delete")
                        .param("evaluatorKind", "LECTURER").param("evaluatorId", "5")
                        .param("assessmentType", "Individual Assessment").param("targetIds", "103").param("reason", "x"))
                .andExpect(redirectedUrl("/admin/progress-tracking"))
                .andExpect(flash().attribute("errorMessage", "Unauthorized access."));

        verify(progressTrackingService, never()).deleteEvaluation(any(), any(), any(), any(), any(), any(), any(), any());
    }

    // --- Adding people who may already have an account ---------------------------------------------------------

    @Test
    void userSuggestions_ReturnMatchingStudentsAsJson() throws Exception {
        when(adminService.suggestStudents("chen")).thenReturn(List.of(
                new AdminService.UserSuggestion(7L, "Chen Wei", "ch***@uni.edu.my", false)));

        mockMvc.perform(get("/admin/users/suggestions").param("type", "student").param("q", "chen"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].id").value(7))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].username").value("Chen Wei"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].inThisCourse").value(false));
    }

    @Test
    void userSuggestions_UseTheAssessorSearchForLecturersAndRefuseUnknownTypes() throws Exception {
        when(adminService.suggestLecturers("lim")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/users/suggestions").param("type", "lecturer").param("q", "lim"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/users/suggestions").param("type", "admin").param("q", "lim"))
                .andExpect(status().isBadRequest());

        verify(adminService, never()).suggestStudents(anyString());
    }

    @Test
    void addExistingStudent_EnrollsThemAndSaysNoNewAccountWasMade() throws Exception {
        Student chen = new Student();
        chen.setId(7L);
        chen.setUsername("Chen Wei");
        when(adminService.enrollExistingStudent(7L)).thenReturn(chen);

        mockMvc.perform(post("/admin/manage-students/add-existing").param("studentId", "7"))
                .andExpect(redirectedUrl("/admin/manage-students"))
                .andExpect(flash().attribute("successMessage", org.hamcrest.Matchers.containsString("already had an account")));
    }

    @Test
    void addExistingStudent_ShowsWhyWhenRefused() throws Exception {
        when(adminService.enrollExistingStudent(7L)).thenThrow(new IllegalArgumentException("This student is already enrolled in this course."));

        mockMvc.perform(post("/admin/manage-students/add-existing").param("studentId", "7"))
                .andExpect(redirectedUrl("/admin/manage-students"))
                .andExpect(flash().attribute("errorMessage", "This student is already enrolled in this course."));
    }

    @Test
    void addExistingLecturer_EnrollsThem() throws Exception {
        Lecturer lim = new Lecturer();
        lim.setId(1L);
        lim.setUsername("Dr Lim");
        when(adminService.enrollExistingLecturer(1L)).thenReturn(lim);

        mockMvc.perform(post("/admin/manage-lecturers/add-existing").param("lecturerId", "1"))
                .andExpect(redirectedUrl("/admin/manage-lecturers"))
                .andExpect(flash().attribute("successMessage", org.hamcrest.Matchers.containsString("Dr Lim already had an account")));
    }

    @Test
    void typingAnExistingStudentsEmailAndSubmittingSaysTheyWereAddedNotCreated() throws Exception {
        Student known = new Student();
        known.setId(7L);
        known.setUsername("Chen Wei");
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(10L);
        when(courseScopeService.getActiveCourseForCurrentUser()).thenReturn(course);
        when(adminService.checkStudentEmailDuplicate(anyString(), any())).thenReturn(null);
        when(adminService.findExistingStudent(anyString(), any())).thenReturn(Optional.of(known));

        mockMvc.perform(post("/admin/manage-students").param("email", "chen@uni.edu.my"))
                .andExpect(redirectedUrl("/admin/manage-students"))
                .andExpect(flash().attribute("successMessage", org.hamcrest.Matchers.containsString("already had an account")));
    }

    @Test
    void searchLecturers_NeverExposesPasswordHashesOrResetTokens() throws Exception {
        Lecturer lim = new Lecturer();
        lim.setId(1L);
        lim.setEmail("lim@uni.edu.my");
        lim.setUsername("Dr Lim");
        lim.setPassword("$2a$10$secretbcrypthash");
        lim.setResetPasswordToken("reset-token-123");
        when(adminService.searchLecturers("lim")).thenReturn(List.of(lim));

        mockMvc.perform(get("/admin/search-lecturers").param("query", "lim"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].email").value("lim@uni.edu.my"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].username").value("Dr Lim"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secretbcrypthash"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("reset-token-123"))));
    }

    // --- Only the creator changes or deletes a course; a co-admin does not ---------------------------------------

    private Course courseCreatedBySomeoneElse() {
        Lecturer creator = new Lecturer();
        creator.setEmail("creator@example.com");
        creator.setUsername("creator");
        Course c = new Course();
        c.setId(10L);
        c.setCourseName("SWE Capstone");
        c.setCourseCode("SECJ101");
        c.setCreatedBy(creator);
        return c;
    }

    @Test
    void testUpdateCourse_ByACoAdmin_IsRefusedAndNothingIsSaved() throws Exception {
        when(superAdminService.getCourseById(10L)).thenReturn(Optional.of(courseCreatedBySomeoneElse()));
        when(courseScopeService.isAdminOfCourse(10L)).thenReturn(true);

        mockMvc.perform(post("/admin/courses/update").param("id", "10").param("courseName", "Hijacked").param("courseCode", "SECJ101"))
                .andExpect(flash().attribute("errorMessage", "Only the admin who created this course can change it."));

        verify(superAdminService, never()).saveCourse(any());
    }

    @Test
    void testUpdateCourse_ByTheCreator_KeepsTheCreator() throws Exception {
        Course stored = courseCreatedBySomeoneElse();
        stored.getCreatedBy().setEmail("admin@example.com");
        when(superAdminService.getCourseById(10L)).thenReturn(Optional.of(stored));
        when(courseScopeService.isAdminOfCourse(10L)).thenReturn(true);
        when(superAdminService.saveCourse(any(Course.class))).thenReturn(stored);

        mockMvc.perform(post("/admin/courses/update").param("id", "10").param("courseName", "Renamed").param("courseCode", "SECJ101"))
                .andExpect(flash().attribute("successMessage", "Course updated successfully!"));

        org.mockito.ArgumentCaptor<Course> saved = org.mockito.ArgumentCaptor.forClass(Course.class);
        verify(superAdminService).saveCourse(saved.capture());
        org.junit.jupiter.api.Assertions.assertEquals("Renamed", saved.getValue().getCourseName());
        org.junit.jupiter.api.Assertions.assertNotNull(saved.getValue().getCreatedBy());
    }

    @Test
    void testDeleteCourse_ByACoAdmin_IsRefused() throws Exception {
        when(superAdminService.getCourseById(10L)).thenReturn(Optional.of(courseCreatedBySomeoneElse()));
        when(courseScopeService.isAdminOfCourse(10L)).thenReturn(true);

        mockMvc.perform(post("/admin/courses/delete/10"))
                .andExpect(flash().attribute("errorMessage", "Only the admin who created this course can delete it."));

        verify(superAdminService, never()).deleteCourse(any());
    }

    // --- Extra fields in a create request are dropped -------------------------------------------------------------

    @Test
    void creatingAStudent_IgnoresAGroupOrPasswordInTheRequest() throws Exception {
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(10L);
        when(courseScopeService.getActiveCourseForCurrentUser()).thenReturn(course);
        when(adminService.checkStudentEmailDuplicate(anyString(), any())).thenReturn(null);

        mockMvc.perform(post("/admin/manage-students")
                .param("email", "new@uni.edu.my")
                .param("group.id", "55")
                .param("password", "chosen-by-attacker"))
                .andExpect(redirectedUrl("/admin/manage-students"));

        org.mockito.ArgumentCaptor<Student> saved = org.mockito.ArgumentCaptor.forClass(Student.class);
        verify(adminService).saveStudent(saved.capture(), any());
        org.junit.jupiter.api.Assertions.assertNull(saved.getValue().getGroup());
        org.junit.jupiter.api.Assertions.assertNull(saved.getValue().getPassword());
        org.junit.jupiter.api.Assertions.assertEquals(10L, saved.getValue().getCourse().getId());
    }

    @Test
    void creatingAnAssessor_CannotChooseRolesOrPasswordAndCannotMakeAnAdmin() throws Exception {
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(10L);
        when(adminService.checkLecturerEmailDuplicate(anyString(), any())).thenReturn(null);

        mockMvc.perform(post("/admin/manage-lecturers")
                .param("email", "new@uni.edu.my")
                .param("roles", "ROLE_ADMIN")
                .param("password", "chosen-by-attacker"))
                .andExpect(redirectedUrl("/admin/manage-lecturers"));

        org.mockito.ArgumentCaptor<Lecturer> saved = org.mockito.ArgumentCaptor.forClass(Lecturer.class);
        verify(adminService).saveLecturer(saved.capture(), any());
        org.junit.jupiter.api.Assertions.assertNull(saved.getValue().getRoles());
        org.junit.jupiter.api.Assertions.assertNull(saved.getValue().getPassword());
        verify(adminService, never()).ensureAdminUserExists(any());
    }

    // --- Shared people are not edited from one course -------------------------------------------------------------

    @Test
    void editingAStudentWhoIsAlsoInAnotherCourse_IsRefusedWhenTheirDetailsChange() throws Exception {
        Student shared = new Student();
        shared.setId(7L);
        shared.setEmail("chen@uni.edu.my");
        shared.setUsername("Chen");
        shared.setCourse(course);
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(10L);
        when(adminService.findStudentById(7L)).thenReturn(Optional.of(shared));
        when(adminService.isStudentInActiveCourse(shared)).thenReturn(true);
        when(adminService.studentBelongsToOtherCourses(shared)).thenReturn(true);

        mockMvc.perform(post("/admin/manage-students").param("id", "7").param("email", "changed@uni.edu.my").param("username", "Chen"))
                .andExpect(flash().attribute("errorMessage", org.hamcrest.Matchers.containsString("also in other courses")));

        verify(adminService, never()).saveStudent(any(), any());
    }

    @Test
    void testSwitchCourse_ToACourseTheAdminOnlyAssessesIn_IsRefused() throws Exception {
        when(courseScopeService.canActAsAdminInCourse(20L)).thenReturn(false);

        mockMvc.perform(post("/admin/switch-course").param("courseId", "20"))
                .andExpect(redirectedUrl("/admin/home"))
                .andExpect(flash().attribute("errorMessage", "You are not authorized to switch to that course."));

        verify(courseScopeService, never()).setActiveCourseIdForCurrentUser(any());
    }

    @Test
    void testUpdateCourse_SavesThePassMarkAndOutcomeTarget() throws Exception {
        Course stored = courseCreatedBySomeoneElse();
        stored.getCreatedBy().setEmail("admin@example.com");
        when(superAdminService.getCourseById(10L)).thenReturn(Optional.of(stored));
        when(courseScopeService.isAdminOfCourse(10L)).thenReturn(true);
        when(superAdminService.saveCourse(any(Course.class))).thenReturn(stored);

        mockMvc.perform(post("/admin/courses/update").param("id", "10").param("courseName", "SWE").param("courseCode", "SECJ101")
                        .param("passMark", "45").param("cloTargetPercent", "70"))
                .andExpect(flash().attribute("successMessage", "Course updated successfully!"));

        org.mockito.ArgumentCaptor<Course> saved = org.mockito.ArgumentCaptor.forClass(Course.class);
        verify(superAdminService).saveCourse(saved.capture());
        org.junit.jupiter.api.Assertions.assertEquals(45.0, saved.getValue().effectivePassMark());
        org.junit.jupiter.api.Assertions.assertEquals(70.0, saved.getValue().effectiveCloTargetPercent());
    }

    @Test
    void testUpdateCourse_RefusesValuesOutsideZeroToOneHundred() throws Exception {
        Course stored = courseCreatedBySomeoneElse();
        stored.getCreatedBy().setEmail("admin@example.com");
        when(superAdminService.getCourseById(10L)).thenReturn(Optional.of(stored));
        when(courseScopeService.isAdminOfCourse(10L)).thenReturn(true);

        mockMvc.perform(post("/admin/courses/update").param("id", "10").param("courseName", "SWE").param("courseCode", "SECJ101")
                        .param("passMark", "150"))
                .andExpect(flash().attribute("errorMessage", "The pass mark and the learning outcome target must be between 0 and 100."));

        verify(superAdminService, never()).saveCourse(any());
    }

    @Test
    void aCourseWithNothingSetUsesFiftyAndFifty() {
        Course c = new Course();
        org.junit.jupiter.api.Assertions.assertEquals(50.0, c.effectivePassMark());
        org.junit.jupiter.api.Assertions.assertEquals(50.0, c.effectiveCloTargetPercent());
    }
}
