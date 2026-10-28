package com.capstone.adproject.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.AssessmentSubmission;
import com.capstone.adproject.model.Course;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.LecturerGroupAssignment;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.SubmissionAnnotation;
import com.capstone.adproject.repositories.AssessmentSubmissionRepository;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.LecturerRubricAssignmentRepository;
import com.capstone.adproject.repositories.LecturerStudentAssignmentRepository;
import com.capstone.adproject.repositories.StudentAssessmentAssignmentRepository;
import com.capstone.adproject.repositories.SubmissionAnnotationRepository;

@ExtendWith(MockitoExtension.class)
class AssessmentSubmissionServiceTest {

    @Mock
    private AssessmentSubmissionRepository submissionRepository;

    @Mock
    private SubmissionAnnotationRepository annotationRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private LecturerGroupAssignmentRepository lecturerGroupAssignmentRepository;

    @Mock
    private LecturerStudentAssignmentRepository lecturerStudentAssignmentRepository;

    @Mock
    private LecturerRubricAssignmentRepository lecturerRubricAssignmentRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private StudentAssessmentAssignmentRepository studentAssessmentAssignmentRepository;

    @InjectMocks
    private AssessmentSubmissionService submissionService;

    private Assessment assessment;
    private Course course;
    private Group group;
    private Student student;
    private Lecturer lecturer;

    @BeforeEach
    void setUp() {
        course = new Course();
        course.setId(1L);

        assessment = new Assessment();
        assessment.setId(10L);
        assessment.setTitle("Final Report");
        assessment.setRequiresSubmission(true);
        assessment.setSubmissionScope("GROUP");
        assessment.setMaxFileSizeMb(25);
        assessment.setCourse(course);

        group = new Group();
        group.setId(5L);
        group.setGroupName("Team Alpha");

        student = new Student();
        student.setId(100L);
        student.setEmail("student@utm.my");
        student.setGroup(group);

        lecturer = new Lecturer();
        lecturer.setId(200L);
        lecturer.setEmail("lecturer@utm.my");
    }

    @Test
    void saveSubmission_newGroupSubmission_savesSuccessfully() throws IOException {
        byte[] pdfBytes = "%PDF-1.4 report content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", pdfBytes);

        when(submissionRepository.findByAssessmentAndGroup(assessment, group)).thenReturn(Optional.empty());
        when(fileStorageService.storePdfFile(eq(file), eq(1L), eq(10L), eq("group_5"))).thenReturn("course_1/assessment_10/group_5/report.pdf");
        when(submissionRepository.save(any(AssessmentSubmission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssessmentSubmission result = submissionService.saveSubmission(assessment, course, group, student, file);

        assertNotNull(result);
        assertEquals("report.pdf", result.getOriginalFileName());
        assertEquals(group, result.getGroup());
        assertEquals(student, result.getStudent());
        assertEquals("SUBMITTED", result.getStatus());
        assertEquals(1, result.getVersion());
    }

    @Test
    void saveSubmission_existingGroupSubmission_replacesFileAndIncrementsVersion() throws IOException {
        byte[] pdfBytes = "%PDF-1.4 updated report content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "report_v2.pdf", "application/pdf", pdfBytes);

        AssessmentSubmission existing = new AssessmentSubmission();
        existing.setId(99L);
        existing.setStoredFileName("course_1/assessment_10/group_5/old_report.pdf");
        existing.setVersion(1);
        existing.setStatus("SUBMITTED");

        when(submissionRepository.findByAssessmentAndGroup(assessment, group)).thenReturn(Optional.of(existing));
        when(fileStorageService.storePdfFile(eq(file), eq(1L), eq(10L), eq("group_5"))).thenReturn("course_1/assessment_10/group_5/report_v2.pdf");
        when(submissionRepository.save(any(AssessmentSubmission.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AssessmentSubmission result = submissionService.saveSubmission(assessment, course, group, student, file);

        assertNotNull(result);
        assertEquals("report_v2.pdf", result.getOriginalFileName());
        assertEquals("RESUBMITTED", result.getStatus());
        assertEquals(2, result.getVersion());
        verify(fileStorageService).deleteFile("course_1/assessment_10/group_5/old_report.pdf");
    }

    @Test
    void saveSubmission_exceedsMaxFileSizeMb_throwsIllegalArgumentException() {
        assessment.setMaxFileSizeMb(1); // 1 MB limit
        byte[] largeBytes = new byte[2 * 1024 * 1024]; // 2 MB
        MockMultipartFile file = new MockMultipartFile("file", "too_large.pdf", "application/pdf", largeBytes);

        assertThrows(IllegalArgumentException.class, () ->
                submissionService.saveSubmission(assessment, course, group, student, file));
    }

    @Test
    void saveOrUpdateAnnotation_newAnnotation_createsSuccessfully() {
        AssessmentSubmission submission = new AssessmentSubmission();
        submission.setId(50L);

        when(annotationRepository.findBySubmissionAndLecturerAndPageNumber(submission, lecturer, 1))
                .thenReturn(Optional.empty());
        when(annotationRepository.save(any(SubmissionAnnotation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String strokesJson = "[{\"tool\":\"pen\",\"color\":\"#4A0018\",\"points\":[{\"x\":0.1,\"y\":0.2}]}]";
        SubmissionAnnotation annotation = submissionService.saveOrUpdateAnnotation(submission, lecturer, 1, strokesJson);

        assertNotNull(annotation);
        assertEquals(1, annotation.getPageNumber());
        assertEquals(strokesJson, annotation.getStrokesJson());
        assertEquals(lecturer, annotation.getLecturer());
        assertEquals(submission, annotation.getSubmission());
    }

    @Test
    void saveOrUpdateAnnotation_existingAnnotation_updatesStrokes() {
        AssessmentSubmission submission = new AssessmentSubmission();
        submission.setId(50L);

        SubmissionAnnotation existing = new SubmissionAnnotation();
        existing.setId(77L);
        existing.setSubmission(submission);
        existing.setLecturer(lecturer);
        existing.setPageNumber(1);
        existing.setStrokesJson("[]");

        when(annotationRepository.findBySubmissionAndLecturerAndPageNumber(submission, lecturer, 1))
                .thenReturn(Optional.of(existing));
        when(annotationRepository.save(any(SubmissionAnnotation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String updatedStrokesJson = "[{\"tool\":\"highlighter\",\"color\":\"#F8A617\"}]";
        SubmissionAnnotation annotation = submissionService.saveOrUpdateAnnotation(submission, lecturer, 1, updatedStrokesJson);

        assertNotNull(annotation);
        assertEquals(77L, annotation.getId());
        assertEquals(updatedStrokesJson, annotation.getStrokesJson());
    }

    @Test
    void getAnnotationsForSubmission_returnsList() {
        AssessmentSubmission submission = new AssessmentSubmission();
        when(annotationRepository.findBySubmissionOrderByPageNumberAsc(submission))
                .thenReturn(List.of(new SubmissionAnnotation()));

        List<SubmissionAnnotation> result = submissionService.getAnnotationsForSubmission(submission);
        assertEquals(1, result.size());
    }

    @Test
    void saveSubmission_exceedsHardCap50Mb_throwsIllegalArgumentException() {
        assessment.setMaxFileSizeMb(60); // Capped at 50 in setter/service
        assertEquals(50, assessment.getMaxFileSizeMb());

        byte[] largeBytes = new byte[51 * 1024 * 1024]; // 51 MB (over 50MB hard cap)
        MockMultipartFile file = new MockMultipartFile("file", "oversized.pdf", "application/pdf", largeBytes);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                submissionService.saveSubmission(assessment, course, group, student, file));
        assertTrue(ex.getMessage().contains("50 MB"));
    }

    @Test
    void getEffectiveSubmissionTitle_customAndDefaultFallback() {
        Assessment a = new Assessment();
        assertEquals("Report / Assignment Submission", a.getEffectiveSubmissionTitle());

        a.setSubmissionTitle("   ");
        assertEquals("Report / Assignment Submission", a.getEffectiveSubmissionTitle());

        a.setSubmissionTitle("Capstone Project Final Report");
        assertEquals("Capstone Project Final Report", a.getEffectiveSubmissionTitle());
    }

    @Test
    void getSubmissionMappingOverview_groupMode_buildsRowsAndKpis() {
        assessment.setSubmissionScope("GROUP");

        Group teamA = new Group();
        teamA.setId(10L);
        teamA.setGroupName("Alpha Squad");
        teamA.setStudents(List.of(student));

        when(groupRepository.findAllWithStudentsByCourseId(1L)).thenReturn(List.of(teamA));
        when(lecturerGroupAssignmentRepository.findByAssessment(assessment)).thenReturn(List.of());
        when(lecturerStudentAssignmentRepository.findByAssessment(assessment)).thenReturn(List.of());
        when(lecturerRubricAssignmentRepository.findByAssessment(assessment)).thenReturn(List.of());
        when(submissionRepository.findByAssessment(assessment)).thenReturn(List.of());

        var overview = submissionService.getSubmissionMappingOverview(assessment);

        assertNotNull(overview);
        assertEquals("GROUP", overview.get("assignmentMode"));
        assertEquals("GROUP", overview.get("submissionScope"));

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> stats = (java.util.Map<String, Object>) overview.get("stats");
        assertEquals(1, stats.get("totalExpected"));
        assertEquals(0L, stats.get("totalSubmitted"));
        assertEquals(1L, stats.get("totalPending"));
        assertEquals(0L, stats.get("totalDoodled"));

        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> rows = (List<java.util.Map<String, Object>>) overview.get("mappings");
        assertEquals(1, rows.size());
        assertEquals("Alpha Squad", rows.get(0).get("targetName"));
        assertEquals(null, rows.get(0).get("submission"));
    }

    @Test
    void saveOrUpdateAnnotation_emptyStrokes_skipsCreationWhenNotFound() {
        AssessmentSubmission submission = new AssessmentSubmission();
        submission.setId(50L);

        when(annotationRepository.findBySubmissionAndLecturerAndPageNumber(submission, lecturer, 2))
                .thenReturn(Optional.empty());

        SubmissionAnnotation annotation = submissionService.saveOrUpdateAnnotation(submission, lecturer, 2, "[]");
        org.junit.jupiter.api.Assertions.assertNull(annotation);
    }

    @Test
    void saveAllAnnotationsFromJson_validJson_savesAnnotations() {
        AssessmentSubmission submission = new AssessmentSubmission();
        submission.setId(50L);

        when(annotationRepository.findBySubmissionAndLecturerAndPageNumber(eq(submission), eq(lecturer), eq(1)))
                .thenReturn(Optional.empty());
        when(annotationRepository.save(any(SubmissionAnnotation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String payload = "{\"1\":[{\"tool\":\"pen\",\"color\":\"#4A0018\",\"points\":[{\"x\":0.1,\"y\":0.2}]}]}";
        submissionService.saveAllAnnotationsFromJson(submission, lecturer, payload);

        verify(annotationRepository).save(any(SubmissionAnnotation.class));
    }

    @Test
    void getSubmissionForTarget_fallbackToMember_returnsSubmission() {
        group.setStudents(List.of(student));
        when(submissionRepository.findByAssessmentAndGroup(assessment, group)).thenReturn(Optional.empty());
        AssessmentSubmission memberSub = new AssessmentSubmission();
        memberSub.setId(88L);
        when(submissionRepository.findByAssessmentAndStudent(assessment, student)).thenReturn(Optional.of(memberSub));

        var result = submissionService.getSubmissionForTarget(assessment, group, null);
        assertTrue(result.isPresent());
        assertEquals(88L, result.get().getId());
    }
}

