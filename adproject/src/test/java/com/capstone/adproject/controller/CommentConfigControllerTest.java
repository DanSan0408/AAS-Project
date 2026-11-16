package com.capstone.adproject.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Course;
import com.capstone.adproject.repositories.AssessmentRepository;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.CourseScopeService;

@ExtendWith(MockitoExtension.class)
class CommentConfigControllerTest {

    @Mock
    private AssessmentService assessmentService;

    @Mock
    private AssessmentRepository assessmentRepository;

    @Mock
    private CourseScopeService courseScopeService;

    @InjectMocks
    private CommentConfigController commentConfigController;

    private Course testCourse;
    private Assessment testAssessment;

    @BeforeEach
    void setUp() {
        testCourse = new Course();
        testCourse.setId(1L);
        testCourse.setCourseCode("SECJ3104");

        testAssessment = new Assessment();
        testAssessment.setId(10L);
        testAssessment.setTitle("Project 1");
        testAssessment.setCourse(testCourse);
        testAssessment.setRubrics(new ArrayList<>());
    }

    @Test
    void testShowTypeSpecificConfig_Authorized_RendersView() {
        when(assessmentService.getAssessmentById(10L)).thenReturn(Optional.of(testAssessment));
        when(courseScopeService.getActiveCourseIdsForCurrentUser()).thenReturn(Set.of(1L));

        Model model = new ConcurrentModel();
        RedirectAttributes ra = new RedirectAttributesModelMap();
        String view = commentConfigController.showTypeSpecificConfig(10L, "group", model, ra);

        assertEquals("comment_configuration_single_type", view);
        assertTrue(model.containsAttribute("assessment"));
        assertEquals("group", model.getAttribute("configType"));
    }

    @Test
    void testSaveTypeSpecificConfig_Valid_PersistsAndRedirects() {
        when(assessmentService.getAssessmentById(10L)).thenReturn(Optional.of(testAssessment));
        when(courseScopeService.getActiveCourseIdsForCurrentUser()).thenReturn(Set.of(1L));

        RedirectAttributes ra = new RedirectAttributesModelMap();
        String view = commentConfigController.saveTypeSpecificConfig(
            10L,
            "individual",
            List.of("Feedback on Contribution", "Leadership Analysis"),
            List.of(20, 50),
            List.of("false", "true"),
            ra
        );

        assertEquals("redirect:/rubrics/manage", view);
        verify(assessmentRepository).save(testAssessment);
        assertTrue(ra.getFlashAttributes().containsKey("successMessage"));
    }

    @Test
    void testSaveTypeSpecificConfig_MultipleQuestions_IndexedParams() {
        when(assessmentService.getAssessmentById(10L)).thenReturn(Optional.of(testAssessment));
        when(courseScopeService.getActiveCourseIdsForCurrentUser()).thenReturn(Set.of(1L));

        RedirectAttributes ra = new RedirectAttributesModelMap();
        String view = commentConfigController.saveTypeSpecificConfig(
            10L,
            "group",
            List.of("Teamwork Analysis", "Milestone Delivery", "Code Quality"),
            List.of(25, 30, 40),
            null,
            java.util.Map.of("commentAnonymous[0]", "true", "commentAnonymous[1]", "false", "commentAnonymous[2]", "true"),
            ra
        );

        assertEquals("redirect:/rubrics/manage", view);
        verify(assessmentRepository).save(testAssessment);
        assertEquals(3, testAssessment.getGroupCommentLabels().size());
        assertEquals("Teamwork Analysis", testAssessment.getGroupCommentLabels().get(0));
        assertEquals("Milestone Delivery", testAssessment.getGroupCommentLabels().get(1));
        assertEquals("Code Quality", testAssessment.getGroupCommentLabels().get(2));
        assertTrue(ra.getFlashAttributes().containsKey("successMessage"));
    }
}

