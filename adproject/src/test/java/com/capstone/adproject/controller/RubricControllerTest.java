package com.capstone.adproject.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.LecturerRubricAssignmentRepository;
import com.capstone.adproject.repositories.LecturerStudentAssignmentRepository;
import com.capstone.adproject.repositories.StudentAssessmentAssignmentRepository;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.CourseScopeService;
import com.capstone.adproject.service.RubricService;
import com.capstone.adproject.service.RubricTemplateService;
import com.capstone.adproject.service.SuperAdminService;

@ExtendWith(MockitoExtension.class)
class RubricControllerTest {

    @Mock
    private RubricService rubricService;

    @Mock
    private AssessmentService assessmentService;

    @Mock
    private CourseScopeService courseScopeService;

    @Mock
    private SuperAdminService superAdminService;

    @Mock
    private RubricTemplateService rubricTemplateService;

    @Mock
    private LecturerGroupAssignmentRepository lecturerGroupAssignmentRepository;

    @Mock
    private LecturerRubricAssignmentRepository lecturerRubricAssignmentRepository;

    @Mock
    private LecturerStudentAssignmentRepository lecturerStudentAssignmentRepository;

    @Mock
    private StudentAssessmentAssignmentRepository studentAssessmentAssignmentRepository;

    @InjectMocks
    private RubricController rubricController;

    private Course testCourse;
    private Assessment testAssessment;
    private Rubric testRubric;

    @BeforeEach
    void setUp() {
        testCourse = new Course();
        testCourse.setId(1L);
        testCourse.setCourseCode("SECJ3104");
        testCourse.setCourseName("App Dev");

        testAssessment = new Assessment();
        testAssessment.setId(10L);
        testAssessment.setTitle("Milestone 1");
        testAssessment.setCourse(testCourse);
        testAssessment.setRubrics(new ArrayList<>());

        testRubric = new Rubric();
        testRubric.setId(50L);
        testRubric.setName("Design Rubric");
        testRubric.setAssessment(testAssessment);
        testRubric.setAssessmentTypes("Group Assessment");
    }

    @Test
    void testManageAssessments_LoadsAssessmentsForActiveCourse() {
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(1L);
        when(assessmentService.findAllAssessmentsWithRubricsByCourseId(1L)).thenReturn(List.of(testAssessment));

        Model model = new ConcurrentModel();
        String view = rubricController.manageAssessments(model);

        assertEquals("manage-assessments", view);
        assertTrue(model.containsAttribute("assessments"));
        assertTrue(model.containsAttribute("newAssessment"));
    }

    @Test
    void testSaveAssessment_Valid_PersistsAndRedirects() {
        when(courseScopeService.getActiveCourseIdForCurrentUser()).thenReturn(1L);
        when(assessmentService.findAllAssessmentsWithRubricsByCourseId(1L)).thenReturn(List.of());
        when(rubricService.findAssessmentById(10L)).thenReturn(testAssessment);
        when(courseScopeService.isActiveCourseId(1L)).thenReturn(true);

        RedirectAttributes ra = new RedirectAttributesModelMap();
        String view = rubricController.saveAssessment(testAssessment, false, ra);

        assertEquals("redirect:/rubrics/manage", view);
        verify(rubricService).saveAssessment(testAssessment);
        assertTrue(ra.getFlashAttributes().containsKey("successMessage"));
    }

    @Test
    void testDeleteAssessment_Success_Redirects() {
        when(rubricService.findAssessmentById(10L)).thenReturn(testAssessment);
        when(courseScopeService.isActiveCourseId(1L)).thenReturn(true);

        RedirectAttributes ra = new RedirectAttributesModelMap();
        String view = rubricController.deleteAssessment(10L, ra);

        assertEquals("redirect:/rubrics/manage", view);
        verify(rubricService).deleteAssessment(10L);
        assertTrue(ra.getFlashAttributes().containsKey("successMessage"));
    }

    @Test
    void testViewAssessmentRubrics_Authorized_RendersRubricsView() {
        when(rubricService.findAssessmentById(10L)).thenReturn(testAssessment);
        when(courseScopeService.isActiveCourseId(1L)).thenReturn(true);

        Model model = new ConcurrentModel();
        RedirectAttributes ra = new RedirectAttributesModelMap();
        String view = rubricController.viewAssessmentRubrics(10L, model, ra);

        assertEquals("view-assessment-rubrics", view);
        assertTrue(model.containsAttribute("assessment"));
    }

    @Test
    void testShowAddRubricForm_Authorized_RendersRubricForm() {
        when(rubricService.findAssessmentById(10L)).thenReturn(testAssessment);
        when(courseScopeService.isActiveCourseId(1L)).thenReturn(true);

        Model model = new ConcurrentModel();
        RedirectAttributes ra = new RedirectAttributesModelMap();
        String view = rubricController.showAddRubricForm(10L, model, ra);

        assertEquals("rubric-form", view);
        assertTrue(model.containsAttribute("rubric"));
        assertTrue(model.containsAttribute("pageTitle"));
    }

    @Test
    void testShowEditRubricForm_Authorized_RendersEditForm() {
        when(rubricService.findRubricById(50L)).thenReturn(testRubric);
        when(courseScopeService.isActiveCourseId(1L)).thenReturn(true);

        Model model = new ConcurrentModel();
        RedirectAttributes ra = new RedirectAttributesModelMap();
        String view = rubricController.showEditRubricForm(50L, model, ra);

        assertEquals("rubric-form", view);
        assertTrue(model.containsAttribute("rubric"));
        assertTrue(model.containsAttribute("pageTitle"));
    }

    @Test
    void testSaveOrUpdateRubric_Valid_PersistsAndRedirects() {
        when(rubricService.findAssessmentById(10L)).thenReturn(testAssessment);
        when(rubricService.findRubricById(50L)).thenReturn(testRubric);
        when(courseScopeService.isActiveCourseId(1L)).thenReturn(true);
        when(rubricService.saveRubric(any(Rubric.class))).thenReturn(testRubric);

        testRubric.setAssessment(testAssessment);
        RedirectAttributes ra = new RedirectAttributesModelMap();
        Model model = new ConcurrentModel();
        String view = rubricController.saveOrUpdateRubric(testRubric, false, Collections.emptyMap(), ra, model);

        assertEquals("redirect:/rubrics/view/10", view);
        verify(rubricService).saveRubric(any(Rubric.class));
        assertTrue(ra.getFlashAttributes().containsKey("successMessage"));
    }

    @Test
    void testDeleteRubric_Success_Redirects() {
        when(rubricService.findRubricById(50L)).thenReturn(testRubric);
        when(courseScopeService.isActiveCourseId(1L)).thenReturn(true);

        RedirectAttributes ra = new RedirectAttributesModelMap();
        String view = rubricController.deleteRubric(50L, ra);

        assertEquals("redirect:/rubrics/view/10", view);
        verify(rubricService).deleteRubric(50L);
        assertTrue(ra.getFlashAttributes().containsKey("successMessage"));
    }

    @Test
    void testMoveRubric_CallsService_Redirects() {
        when(rubricService.findRubricById(50L)).thenReturn(testRubric);
        when(courseScopeService.isActiveCourseId(1L)).thenReturn(true);

        RedirectAttributes ra = new RedirectAttributesModelMap();
        String view = rubricController.moveRubric(50L, "up", ra);

        assertEquals("redirect:/rubrics/view/10#rubric-50", view);
        verify(rubricService).moveRubric(50L, "up");
    }

    @Test
    void testMoveAssessmentBlock_CallsService_Redirects() {
        when(rubricService.findAssessmentById(10L)).thenReturn(testAssessment);
        when(courseScopeService.isActiveCourseId(1L)).thenReturn(true);

        RedirectAttributes ra = new RedirectAttributesModelMap();
        String view = rubricController.moveAssessmentBlock(10L, "group", "down", ra);

        assertEquals("redirect:/rubrics/view/10#group-block", view);
        verify(rubricService).moveAssessmentBlock(10L, "group", "down");
    }
}
