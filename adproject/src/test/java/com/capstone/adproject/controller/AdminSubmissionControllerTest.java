package com.capstone.adproject.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.AssessmentSubmission;
import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.SubmissionAnnotation;
import com.capstone.adproject.service.AdminService;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.AssessmentSubmissionService;
import com.capstone.adproject.service.CourseScopeService;
import com.capstone.adproject.service.DeadlineService;
import com.capstone.adproject.service.RubricService;

@ExtendWith(MockitoExtension.class)
class AdminSubmissionControllerTest {

    @Mock
    private AdminService adminService;

    @Mock
    private AssessmentService assessmentService;

    @Mock
    private DeadlineService deadlineService;

    @Mock
    private RubricService rubricService;

    @Mock
    private CourseScopeService courseScopeService;

    @Mock
    private AssessmentSubmissionService assessmentSubmissionService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AdminController adminController;

    private Course testCourse;
    private Assessment testAssessment;
    private AssessmentSubmission testSubmission;

    @BeforeEach
    void setUp() {
        testCourse = new Course();
        testCourse.setId(10L);
        testCourse.setCourseCode("SECJ3104");
        testCourse.setCourseName("Application Development");

        testAssessment = new Assessment();
        testAssessment.setId(100L);
        testAssessment.setTitle("Final Report Submission");
        testAssessment.setCourse(testCourse);
        testAssessment.setRequiresSubmission(true);
        testAssessment.setSubmissionScope("GROUP");
        testAssessment.setMaxFileSizeMb(50);

        testSubmission = new AssessmentSubmission();
        testSubmission.setId(500L);
        testSubmission.setAssessment(testAssessment);
        testSubmission.setOriginalFileName("Team_Report.pdf");
    }

    @Test
    void testShowAssessmentSubmissionConfig_Authorized_ReturnsConfigView() {
        when(assessmentService.getAssessmentById(100L)).thenReturn(Optional.of(testAssessment));
        when(courseScopeService.isActiveCourseId(10L)).thenReturn(true);

        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("admin_user");

        Model model = new ConcurrentModel();
        RedirectAttributes ra = new RedirectAttributesModelMap();
        String view = adminController.showAssessmentSubmissionConfig(100L, model, ra);

        assertEquals("assessment_submission_configuration", view);
        assertTrue(model.containsAttribute("assessment"));
        assertEquals("admin_user", model.getAttribute("adminUsername"));
    }

    @Test
    void testShowAssessmentSubmissionConfig_Unauthorized_RedirectsToManage() {
        when(assessmentService.getAssessmentById(100L)).thenReturn(Optional.of(testAssessment));
        when(courseScopeService.isActiveCourseId(10L)).thenReturn(false);

        Model model = new ConcurrentModel();
        RedirectAttributes ra = new RedirectAttributesModelMap();
        String view = adminController.showAssessmentSubmissionConfig(100L, model, ra);

        assertEquals("redirect:/rubrics/manage", view);
        assertTrue(ra.getFlashAttributes().containsKey("errorMessage"));
    }

    @Test
    void testSaveAssessmentSubmissionConfig_ValidSettings_PersistsAndCapsAt50MB() {
        when(assessmentService.getAssessmentById(100L)).thenReturn(Optional.of(testAssessment));
        when(courseScopeService.isActiveCourseId(10L)).thenReturn(true);

        RedirectAttributes ra = new RedirectAttributesModelMap();
        String view = adminController.saveAssessmentSubmissionConfig(
            100L,
            true,
            "Final Software Architecture Document",
            "INDIVIDUAL",
            100, // Exceeds 50MB ceiling, should be capped to 50
            "Submit IEEE format report",
            null,
            ra
        );

        assertEquals("redirect:/admin/assessment/100/submission-config", view);
        verify(rubricService).saveAssessment(testAssessment);
        assertEquals(true, testAssessment.getRequiresSubmission());
        assertEquals("Final Software Architecture Document", testAssessment.getSubmissionTitle());
        assertEquals("INDIVIDUAL", testAssessment.getSubmissionScope());
        assertEquals(50, testAssessment.getMaxFileSizeMb()); // Verified capped to 50
        assertEquals("Submit IEEE format report", testAssessment.getSubmissionInstructions());
        assertTrue(ra.getFlashAttributes().containsKey("successMessage"));
    }

    @Test
    void testDownloadSubmissionFileAdmin_Found_ReturnsPdfStream() {
        when(assessmentSubmissionService.getSubmissionById(500L)).thenReturn(Optional.of(testSubmission));
        when(courseScopeService.isActiveCourseId(10L)).thenReturn(true);

        Resource dummyResource = new InputStreamResource(new ByteArrayInputStream(new byte[]{1, 2, 3, 4}));
        when(assessmentSubmissionService.getSubmissionFileResource(testSubmission)).thenReturn(dummyResource);

        RedirectAttributes ra = new RedirectAttributesModelMap();
        ResponseEntity<Resource> response = adminController.downloadSubmissionFileAdmin(500L, ra);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void testViewSubmissionWithDoodlesAdmin_Authorized_ReturnsViewerWithReadOnlyMode() {
        when(assessmentSubmissionService.getSubmissionById(500L)).thenReturn(Optional.of(testSubmission));
        when(courseScopeService.isActiveCourseId(10L)).thenReturn(true);

        Model model = new ConcurrentModel();
        RedirectAttributes ra = new RedirectAttributesModelMap();
        String view = adminController.viewSubmissionDoodlesAdmin(500L, model, ra);

        assertEquals("student_submission_view", view);
        assertTrue(model.containsAttribute("assessment"));
        assertTrue(model.containsAttribute("submission"));
        assertEquals(true, model.getAttribute("readOnly"));
        assertEquals(true, model.getAttribute("isAdminView"));
    }

    @Test
    void testGetSubmissionAnnotationsAdmin_Authorized_ReturnsAnnotationsList() {
        when(assessmentSubmissionService.getSubmissionById(500L)).thenReturn(Optional.of(testSubmission));
        when(courseScopeService.isActiveCourseId(10L)).thenReturn(true);

        SubmissionAnnotation ann = new SubmissionAnnotation();
        ann.setId(10L);
        ann.setPageNumber(1);
        when(assessmentSubmissionService.getAnnotationsForSubmission(testSubmission)).thenReturn(List.of(ann));

        ResponseEntity<List<SubmissionAnnotation>> resp = adminController.getSubmissionAnnotationsAdmin(500L);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(1, resp.getBody().size());
    }
}
