package com.capstone.adproject.controller;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Course;
import com.capstone.adproject.service.AdminService;
import com.capstone.adproject.service.AiServiceClient;
import com.capstone.adproject.service.AssessmentCommentService;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.CourseScopeService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The scan and the safe approval touch only comments of an assessment in the administrator's active course. */
@ExtendWith(MockitoExtension.class)
class AdminCommentScanTest {

    @Mock private AssessmentService assessmentService;
    @Mock private CourseScopeService courseScopeService;
    @Mock private AssessmentCommentService assessmentCommentService;
    @Mock private AdminService adminService;

    @InjectMocks private AdminController controller;

    private Assessment assessment;

    @BeforeEach
    void setUp() {
        Course course = new Course();
        course.setId(3L);
        assessment = new Assessment();
        assessment.setId(9L);
        assessment.setCourse(course);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin1", "n/a", List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aScanOfAnotherCoursesAssessmentIsRefused() {
        when(assessmentService.getAssessmentById(9L)).thenReturn(Optional.of(assessment));
        when(courseScopeService.isActiveCourseId(3L)).thenReturn(false);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.aiScanComments(9L, redirect);

        assertEquals("redirect:/admin/progress-tracking", view);
        verify(assessmentCommentService, never()).scanPendingComments(9L);
    }

    @Test
    void aScanReportsWhatItFlaggedAndWhatIsLeft() {
        when(assessmentService.getAssessmentById(9L)).thenReturn(Optional.of(assessment));
        when(courseScopeService.isActiveCourseId(3L)).thenReturn(true);
        when(assessmentCommentService.scanPendingComments(9L))
                .thenReturn(new AssessmentCommentService.ScanResult(40, 3, 0, 20, "OK", List.of()));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.aiScanComments(9L, redirect);

        String message = (String) redirect.getFlashAttributes().get("successMessage");
        assertTrue(message.contains("Read 40 comment(s); 3 flagged"), message);
        assertTrue(message.contains("20 more are waiting"), message);
    }

    @Test
    void aScanWhereTheAiCouldNotReadSomeCommentsIsReportedAsAProblemNotASuccess() {
        when(assessmentService.getAssessmentById(9L)).thenReturn(Optional.of(assessment));
        when(courseScopeService.isActiveCourseId(3L)).thenReturn(true);
        when(assessmentCommentService.scanPendingComments(9L))
                .thenReturn(new AssessmentCommentService.ScanResult(0, 0, 5, 0, "UNAVAILABLE", List.of("The AI was not available.")));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.aiScanComments(9L, redirect);

        assertTrue(redirect.getFlashAttributes().containsKey("errorMessage"));
        assertTrue(!redirect.getFlashAttributes().containsKey("successMessage"));
    }

    @Test
    void anAiFailureLeavesCommentsUnchangedAndSaysSo() {
        when(assessmentService.getAssessmentById(9L)).thenReturn(Optional.of(assessment));
        when(courseScopeService.isActiveCourseId(3L)).thenReturn(true);
        when(assessmentCommentService.scanPendingComments(9L)).thenThrow(new RuntimeException("down"));
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.aiScanComments(9L, redirect);

        String message = (String) redirect.getFlashAttributes().get("errorMessage");
        assertTrue(message.contains("unchanged"), message);
    }

    @Test
    void theSafeApprovalUsesOnlyTheScannedSafePath() {
        when(assessmentService.getAssessmentById(9L)).thenReturn(Optional.of(assessment));
        when(courseScopeService.isActiveCourseId(3L)).thenReturn(true);
        when(assessmentCommentService.bulkApproveSafeComments(9L, "admin1")).thenReturn(4);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.bulkApproveSafeComments(9L, redirect);

        verify(assessmentCommentService).bulkApproveSafeComments(9L, "admin1");
        verify(assessmentCommentService, never()).bulkApproveAllComments(9L, "admin1");
    }
}
