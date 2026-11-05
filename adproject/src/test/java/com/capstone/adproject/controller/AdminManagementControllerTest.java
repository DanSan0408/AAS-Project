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
        when(courseScopeService.isManagedCourseId(10L)).thenReturn(true);

        mockMvc.perform(get("/admin/courses/edit/10"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin_edit_course"))
                .andExpect(model().attributeExists("course", "adminUsername"));
    }

    @Test
    void testEditCourse_Unauthorized_Redirects() throws Exception {
        when(superAdminService.getCourseById(999L)).thenReturn(Optional.of(course));
        when(courseScopeService.isManagedCourseId(10L)).thenReturn(false);

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
        when(courseScopeService.isManagedCourseId(10L)).thenReturn(true);
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
        when(courseScopeService.isManagedCourseId(10L)).thenReturn(false);

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
        when(courseScopeService.isManagedCourseId(10L)).thenReturn(true);

        mockMvc.perform(post("/admin/courses/delete/10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/manage-courses"))
                .andExpect(flash().attribute("successMessage", "Course deleted successfully!"));

        verify(superAdminService).deleteCourse(10L);
    }

    @Test
    void testDeleteCourse_Unauthorized_Redirects() throws Exception {
        when(courseScopeService.isManagedCourseId(10L)).thenReturn(false);

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
}
