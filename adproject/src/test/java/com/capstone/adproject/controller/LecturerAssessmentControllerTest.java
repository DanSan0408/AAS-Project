package com.capstone.adproject.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.AssessmentSubmission;
import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.Deadline;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.SubmissionAnnotation;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.LecturerRubricAssignmentRepository;
import com.capstone.adproject.repositories.LecturerStudentAssignmentRepository;
import com.capstone.adproject.service.AssessmentSubmissionService;
import com.capstone.adproject.service.DeadlineService;
import com.capstone.adproject.service.LecturerAssessmentService;
import com.capstone.adproject.service.RubricService;

@ExtendWith(MockitoExtension.class)
class LecturerAssessmentControllerTest {

    @Mock
    private LecturerAssessmentService lecturerAssessmentService;

    @Mock
    private RubricService rubricService;

    @Mock
    private DeadlineService deadlineService;

    @Mock
    private LecturerRepository lecturerRepository;

    @Mock
    private LecturerGroupAssignmentRepository assignmentRepository;

    @Mock
    private LecturerStudentAssignmentRepository studentAssignmentRepository;

    @Mock
    private LecturerRubricAssignmentRepository rubricAssignmentRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private AssessmentSubmissionService assessmentSubmissionService;

    @InjectMocks
    private LecturerAssessmentController controller;

    private Lecturer testLecturer;
    private Assessment testAssessment;
    private Course testCourse;
    private Student testStudent;
    private Group testGroup;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        testCourse = new Course();
        testCourse.setId(1L);
        testCourse.setCourseCode("SECJ3104");
        testCourse.setCourseName("App Dev");

        testLecturer = new Lecturer();
        testLecturer.setId(5L);
        testLecturer.setEmail("lecturer@utm.my");
        testLecturer.setUsername("dr_lecturer");

        testAssessment = new Assessment();
        testAssessment.setId(10L);
        testAssessment.setTitle("Project 1");
        testAssessment.setCourse(testCourse);
        testAssessment.setRubrics(new ArrayList<>());

        testGroup = new Group();
        testGroup.setId(20L);
        testGroup.setGroupName("Group Alpha");
        testGroup.setCourse(testCourse);

        testStudent = new Student();
        testStudent.setId(100L);
        testStudent.setEmail("student@utm.my");
        testStudent.setUsername("stud_1");
        testStudent.setGroup(testGroup);

        auth = new UsernamePasswordAuthenticationToken("lecturer@utm.my", "password");
    }

    @Test
    void testShowAssessments_LoadsAssessmentsList() {
        when(lecturerRepository.findByEmail("lecturer@utm.my")).thenReturn(Optional.of(testLecturer));
        when(assignmentRepository.findAssessmentsByLecturer(testLecturer)).thenReturn(List.of(testAssessment));
        when(rubricAssignmentRepository.findAssessmentsByLecturer(testLecturer)).thenReturn(List.of());
        when(studentAssignmentRepository.findAssessmentsByLecturer(testLecturer)).thenReturn(List.of());
        when(deadlineService.getDeadlinesByAssessmentId(10L)).thenReturn(List.of());

        Model model = new ConcurrentModel();
        String view = controller.showAssessments(model, auth);

        assertEquals("lecturer_assessments", view);
        assertTrue(model.containsAttribute("assessments"));
        assertTrue(model.containsAttribute("assessmentLaunchModes"));
    }

    @Test
    void testShowStudentSelection_ReturnsSelectStudentView() {
        Deadline deadline = new Deadline();
        deadline.setAssessorType("LECTURER");
        deadline.setOpenDate(new Date(System.currentTimeMillis() - 86400000L)); // yesterday
        deadline.setDate(new Date(System.currentTimeMillis() + 86400000L));     // tomorrow

        when(lecturerRepository.findByEmail("lecturer@utm.my")).thenReturn(Optional.of(testLecturer));
        when(rubricService.findAssessmentById(10L)).thenReturn(testAssessment);
        when(deadlineService.getDeadlinesByAssessmentId(10L)).thenReturn(List.of(deadline));
        when(studentAssignmentRepository.findStudentsByLecturerAndAssessment(testLecturer, testAssessment))
                .thenReturn(List.of(testStudent));
        when(lecturerAssessmentService.getStudentEvaluationStatus(testAssessment, testLecturer, 100L))
                .thenReturn("not_started");

        Model model = new ConcurrentModel();
        RedirectAttributes ra = new RedirectAttributesModelMap();
        String view = controller.showStudentSelection(10L, model, auth, ra);

        assertEquals("lecturer_select_student", view);
        assertTrue(model.containsAttribute("assignedStudents"));
        assertTrue(model.containsAttribute("studentEvaluationStatus"));
    }

    @Test
    void testShowCombinedEvaluationForm_StudentTarget_WithSubmission_LoadsPdfAndAnnotations() {
        testAssessment.setRequiresSubmission(true);
        Rubric rubric = new Rubric();
        rubric.setId(50L);
        rubric.setAssessmentTypes("Individual Assessment");
        testAssessment.setRubrics(List.of(rubric));

        AssessmentSubmission submission = new AssessmentSubmission();
        submission.setId(77L);
        submission.setOriginalFileName("Report.pdf");

        when(lecturerRepository.findByEmail("lecturer@utm.my")).thenReturn(Optional.of(testLecturer));
        when(rubricService.findAssessmentById(10L)).thenReturn(testAssessment);
        when(studentAssignmentRepository.findStudentsByLecturerAndAssessment(testLecturer, testAssessment))
                .thenReturn(List.of(testStudent));
        when(lecturerAssessmentService.getStudentEvaluationStatus(testAssessment, testLecturer, 100L))
                .thenReturn("not_started");
        when(assessmentSubmissionService.getSubmissionForTarget(testAssessment, null, testStudent))
                .thenReturn(Optional.of(submission));

        Model model = new ConcurrentModel();
        RedirectAttributes ra = new RedirectAttributesModelMap();
        String view = controller.showCombinedEvaluationForm(10L, null, 100L, null, model, auth, ra);

        assertEquals("lecturer_combined_evaluation_form", view);
        assertTrue(model.containsAttribute("submission"));
        assertEquals(submission, model.getAttribute("submission"));
        assertTrue((Boolean) model.getAttribute("isStudentTarget"));
    }

    @Test
    void testShowCombinedEvaluationForm_StudentTarget_WithoutSubmission_MaintainsRubricLayout() {
        testAssessment.setRequiresSubmission(false);
        Rubric rubric = new Rubric();
        rubric.setId(50L);
        rubric.setAssessmentTypes("Individual Assessment");
        testAssessment.setRubrics(List.of(rubric));

        when(lecturerRepository.findByEmail("lecturer@utm.my")).thenReturn(Optional.of(testLecturer));
        when(rubricService.findAssessmentById(10L)).thenReturn(testAssessment);
        when(studentAssignmentRepository.findStudentsByLecturerAndAssessment(testLecturer, testAssessment))
                .thenReturn(List.of(testStudent));
        when(lecturerAssessmentService.getStudentEvaluationStatus(testAssessment, testLecturer, 100L))
                .thenReturn("not_started");

        Model model = new ConcurrentModel();
        RedirectAttributes ra = new RedirectAttributesModelMap();
        String view = controller.showCombinedEvaluationForm(10L, null, 100L, null, model, auth, ra);

        assertEquals("lecturer_combined_evaluation_form", view);
        assertEquals(null, model.getAttribute("submission"));
    }

    @Test
    void testSubmitCombinedEvaluation_StudentTarget_SavesScoresAndRedirects() {
        when(lecturerRepository.findByEmail("lecturer@utm.my")).thenReturn(Optional.of(testLecturer));
        when(rubricService.findAssessmentById(10L)).thenReturn(testAssessment);
        when(studentAssignmentRepository.findStudentsByLecturerAndAssessment(testLecturer, testAssessment))
                .thenReturn(List.of(testStudent));
        when(lecturerAssessmentService.isStudentEvaluationComplete(testAssessment, testLecturer, 100L))
                .thenReturn(true);

        Map<String, String> params = Map.of(
            "student_100_rubric_50", "4",
            "student_100_comment_0", "Excellent report analysis"
        );

        RedirectAttributes ra = new RedirectAttributesModelMap();
        String redirect = controller.submitCombinedEvaluation(10L, null, 100L, params, auth, ra);

        assertEquals("redirect:/lecturer/assessments/10/select-student", redirect);
        verify(lecturerAssessmentService).saveEvaluationScores(
            eq(10L), eq(5L), eq("lecturer@utm.my"), eq(100L), eq(false), eq("Individual Assessment"),
            any(), any(), any()
        );
        assertTrue(ra.getFlashAttributes().containsKey("successMessage"));
    }

    @Test
    void testStreamSubmissionFile_Found_ReturnsPdfResource() {
        when(lecturerRepository.findByEmail("lecturer@utm.my")).thenReturn(Optional.of(testLecturer));
        AssessmentSubmission sub = new AssessmentSubmission();
        sub.setId(77L);
        sub.setOriginalFileName("test.pdf");

        Resource dummyResource = new InputStreamResource(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        when(assessmentSubmissionService.getSubmissionById(77L)).thenReturn(Optional.of(sub));
        when(assessmentSubmissionService.getSubmissionFileResource(sub)).thenReturn(dummyResource);

        ResponseEntity<Resource> response = controller.streamSubmissionFile(77L, auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void testStreamSubmissionFile_NotFound_Returns404() {
        when(lecturerRepository.findByEmail("lecturer@utm.my")).thenReturn(Optional.of(testLecturer));
        when(assessmentSubmissionService.getSubmissionById(999L)).thenReturn(Optional.empty());

        ResponseEntity<Resource> response = controller.streamSubmissionFile(999L, auth);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testGetSubmissionAnnotations_ReturnsList() {
        when(lecturerRepository.findByEmail("lecturer@utm.my")).thenReturn(Optional.of(testLecturer));
        AssessmentSubmission sub = new AssessmentSubmission();
        sub.setId(77L);
        SubmissionAnnotation ann = new SubmissionAnnotation();
        ann.setId(1L);
        ann.setPageNumber(1);

        when(assessmentSubmissionService.getSubmissionById(77L)).thenReturn(Optional.of(sub));
        when(assessmentSubmissionService.getAnnotationsForSubmission(sub)).thenReturn(List.of(ann));

        ResponseEntity<List<SubmissionAnnotation>> resp = controller.getSubmissionAnnotations(77L, auth);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(1, resp.getBody().size());
    }

    @Test
    void testSaveSubmissionAnnotation_Valid_PersistsAnnotation() {
        when(lecturerRepository.findByEmail("lecturer@utm.my")).thenReturn(Optional.of(testLecturer));
        AssessmentSubmission sub = new AssessmentSubmission();
        sub.setId(77L);
        when(assessmentSubmissionService.getSubmissionById(77L)).thenReturn(Optional.of(sub));

        SubmissionAnnotation savedAnn = new SubmissionAnnotation();
        savedAnn.setId(101L);
        savedAnn.setPageNumber(2);
        when(assessmentSubmissionService.saveOrUpdateAnnotation(eq(sub), eq(testLecturer), eq(2), anyString()))
                .thenReturn(savedAnn);

        Map<String, Object> payload = Map.of(
            "pageNumber", 2,
            "strokesJson", "[{\"tool\":\"pen\"}]"
        );

        ResponseEntity<Map<String, Object>> resp = controller.saveSubmissionAnnotation(77L, payload, auth);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("success", resp.getBody().get("status"));
        assertEquals(101L, resp.getBody().get("annotationId"));
    }
}
