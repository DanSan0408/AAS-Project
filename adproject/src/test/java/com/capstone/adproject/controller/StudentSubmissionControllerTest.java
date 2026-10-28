package com.capstone.adproject.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.security.Principal;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.AssessmentSubmission;
import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.SubmissionAnnotation;
import com.capstone.adproject.repositories.AssessmentRepository;
import com.capstone.adproject.repositories.CourseRepository;
import com.capstone.adproject.repositories.StudentRepository;
import com.capstone.adproject.service.AssessmentSubmissionService;
import com.capstone.adproject.service.DeadlineService;

@ExtendWith(MockitoExtension.class)
class StudentSubmissionControllerTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private AssessmentRepository assessmentRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private AssessmentSubmissionService assessmentSubmissionService;

    @Mock
    private DeadlineService deadlineService;

    @InjectMocks
    private StudentController studentController;

    private Student testStudent;
    private Course testCourse;
    private Assessment testAssessment;
    private Group testGroup;
    private Principal principal;

    @BeforeEach
    void setUp() {
        testCourse = new Course();
        testCourse.setId(10L);
        testCourse.setCourseCode("SECJ3104");
        testCourse.setCourseName("Application Development");

        testGroup = new Group();
        testGroup.setId(20L);
        testGroup.setGroupName("Group 1");
        testGroup.setCourse(testCourse);

        testStudent = new Student();
        testStudent.setId(1L);
        testStudent.setEmail("student@utm.my");
        testStudent.setUsername("ali");
        testStudent.setCourse(testCourse);
        testStudent.setGroup(testGroup);

        testAssessment = new Assessment();
        testAssessment.setId(100L);
        testAssessment.setTitle("Project Report");
        testAssessment.setRequiresSubmission(true);
        testAssessment.setSubmissionScope("INDIVIDUAL");
        testAssessment.setCourse(testCourse);

        principal = () -> "student@utm.my";
    }

    @Test
    void testListStudentSubmissions_ReturnsSubmissionsViewWithDoodleCountMap() {
        when(studentRepository.findByEmail("student@utm.my")).thenReturn(Optional.of(testStudent));
        when(assessmentRepository.findAll()).thenReturn(List.of(testAssessment));
        when(courseRepository.findById(10L)).thenReturn(Optional.of(testCourse));
        when(deadlineService.getDeadlinesByAssessmentId(100L)).thenReturn(List.of());
        when(assessmentSubmissionService.getSubmissionForStudent(testAssessment, testStudent)).thenReturn(Optional.empty());

        Model model = new ConcurrentModel();
        String view = studentController.listStudentSubmissions(model, principal);

        assertEquals("student_submissions", view);
        assertTrue(model.containsAttribute("assessments"));
        assertTrue(model.containsAttribute("doodleCountMap"));
        @SuppressWarnings("unchecked")
        Map<Long, Integer> doodleCountMap = (Map<Long, Integer>) model.getAttribute("doodleCountMap");
        assertNotNull(doodleCountMap);
        assertEquals(0, doodleCountMap.get(100L));
        assertEquals(1, model.getAttribute("totalRequired"));
        assertEquals(0, model.getAttribute("totalSubmitted"));
        assertEquals(1, model.getAttribute("totalPending"));
    }

    @Test
    void testListStudentSubmissions_WithSubmittedReportAndDoodles() {
        AssessmentSubmission sub = new AssessmentSubmission();
        sub.setId(50L);
        sub.setOriginalFileName("MyReport.pdf");
        sub.setFileSizeBytes(1024L * 1024L * 2); // 2MB

        SubmissionAnnotation annotation = new SubmissionAnnotation();
        annotation.setId(1L);

        when(studentRepository.findByEmail("student@utm.my")).thenReturn(Optional.of(testStudent));
        when(assessmentRepository.findAll()).thenReturn(List.of(testAssessment));
        when(courseRepository.findById(10L)).thenReturn(Optional.of(testCourse));
        when(deadlineService.getDeadlinesByAssessmentId(100L)).thenReturn(List.of());
        when(assessmentSubmissionService.getSubmissionForStudent(testAssessment, testStudent)).thenReturn(Optional.of(sub));
        when(assessmentSubmissionService.getAnnotationsForSubmission(sub)).thenReturn(List.of(annotation));

        Model model = new ConcurrentModel();
        String view = studentController.listStudentSubmissions(model, principal);

        assertEquals("student_submissions", view);
        @SuppressWarnings("unchecked")
        Map<Long, Integer> doodleCountMap = (Map<Long, Integer>) model.getAttribute("doodleCountMap");
        assertNotNull(doodleCountMap);
        assertEquals(1, doodleCountMap.get(100L));
        assertEquals(1, model.getAttribute("totalRequired"));
        assertEquals(1, model.getAttribute("totalSubmitted"));
        assertEquals(0, model.getAttribute("totalPending"));
        assertEquals(1, model.getAttribute("totalWithDoodles"));
    }

    @Test
    void testShowAssessmentSubmitPage_Success() {
        when(studentRepository.findByEmail("student@utm.my")).thenReturn(Optional.of(testStudent));
        when(assessmentRepository.findById(100L)).thenReturn(Optional.of(testAssessment));
        when(deadlineService.getDeadlinesByAssessmentId(100L)).thenReturn(List.of());
        when(assessmentSubmissionService.getSubmissionForStudent(testAssessment, testStudent)).thenReturn(Optional.empty());

        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        Model model = new ConcurrentModel();

        String view = studentController.showAssessmentSubmitPage(100L, model, principal, redirectAttributes);

        assertEquals("student_submit_single", view);
        assertEquals(testAssessment, model.getAttribute("assessment"));
        assertEquals(testStudent, model.getAttribute("currentStudent"));
        assertEquals(0, model.getAttribute("doodleCount"));
    }

    @Test
    void testDownloadSubmissionFile_StudentAuthorized_ReturnsPdf() {
        AssessmentSubmission sub = new AssessmentSubmission();
        sub.setId(50L);
        sub.setOriginalFileName("FinalReport.pdf");

        Resource dummyResource = new InputStreamResource(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        when(studentRepository.findByEmail("student@utm.my")).thenReturn(Optional.of(testStudent));
        when(assessmentRepository.findById(100L)).thenReturn(Optional.of(testAssessment));
        when(assessmentSubmissionService.getSubmissionForStudent(testAssessment, testStudent)).thenReturn(Optional.of(sub));
        when(assessmentSubmissionService.getSubmissionFileResource(sub)).thenReturn(dummyResource);

        ResponseEntity<Resource> response = studentController.downloadSubmissionFile(100L, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void testGetSubmissionAnnotations_ReturnsList() {
        AssessmentSubmission sub = new AssessmentSubmission();
        sub.setId(50L);
        SubmissionAnnotation ann = new SubmissionAnnotation();
        ann.setId(10L);

        when(studentRepository.findByEmail("student@utm.my")).thenReturn(Optional.of(testStudent));
        when(assessmentRepository.findById(100L)).thenReturn(Optional.of(testAssessment));
        when(assessmentSubmissionService.getSubmissionForStudent(testAssessment, testStudent)).thenReturn(Optional.of(sub));
        when(assessmentSubmissionService.getAnnotationsForSubmission(sub)).thenReturn(List.of(ann));

        ResponseEntity<List<SubmissionAnnotation>> response = studentController.getSubmissionAnnotations(100L, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void testViewSubmissionWithDoodles_LoadsViewerWithPdfUrl() {
        AssessmentSubmission sub = new AssessmentSubmission();
        sub.setId(50L);
        sub.setOriginalFileName("FinalReport.pdf");

        when(studentRepository.findByEmail("student@utm.my")).thenReturn(Optional.of(testStudent));
        when(assessmentRepository.findById(100L)).thenReturn(Optional.of(testAssessment));
        when(assessmentSubmissionService.getSubmissionForStudent(testAssessment, testStudent)).thenReturn(Optional.of(sub));

        Model model = new ConcurrentModel();
        RedirectAttributes ra = new RedirectAttributesModelMap();
        String view = studentController.viewSubmissionWithDoodles(100L, model, principal, ra);

        assertEquals("student_submission_view", view);
        assertEquals(sub, model.getAttribute("submission"));
        assertEquals(true, model.getAttribute("readOnly"));
        assertEquals("/student/assessment/100/submission/file", model.getAttribute("pdfUrl"));
    }

    @Test
    void testUploadAssessmentSubmission_ValidPdf_PersistsAndRedirects() throws Exception {
        when(studentRepository.findByEmail("student@utm.my")).thenReturn(Optional.of(testStudent));
        when(assessmentRepository.findById(100L)).thenReturn(Optional.of(testAssessment));
        when(deadlineService.getDeadlinesByAssessmentId(100L)).thenReturn(List.of());

        MockMultipartFile file = new MockMultipartFile("file", "project.pdf", "application/pdf", new byte[]{1, 2, 3});
        RedirectAttributes ra = new RedirectAttributesModelMap();

        String view = studentController.uploadAssessmentSubmission(100L, file, "single", principal, ra);

        assertEquals("redirect:/student/assessment/100/submit", view);
        verify(assessmentSubmissionService).saveSubmission(eq(testAssessment), eq(testCourse), eq(testGroup), eq(testStudent), eq(file));
        assertTrue(ra.getFlashAttributes().containsKey("success"));
    }
}
