package com.capstone.adproject.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.AssessmentComment;
import com.capstone.adproject.model.EvaluationDeletionLog;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Mark;
import com.capstone.adproject.model.Rating;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.SubRubric;
import com.capstone.adproject.repositories.AssessmentCommentRepository;
import com.capstone.adproject.repositories.EvaluationDeletionLogRepository;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.LecturerRubricAssignmentRepository;
import com.capstone.adproject.repositories.LecturerStudentAssignmentRepository;
import com.capstone.adproject.repositories.MarkRepository;
import com.capstone.adproject.repositories.StudentAssessmentAssignmentRepository;
import com.capstone.adproject.service.ProgressTrackingService.EvaluationRow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Admin view and delete of finished evaluations: what is listed, how it is filtered, and what a delete removes. */
class ProgressTrackingEvaluationsTest {

    private final MarkRepository markRepository = mock(MarkRepository.class);
    private final AssessmentCommentRepository commentRepository = mock(AssessmentCommentRepository.class);
    private final EvaluationDeletionLogRepository logRepository = mock(EvaluationDeletionLogRepository.class);

    private final ProgressTrackingService service = new ProgressTrackingService(
            mock(LecturerGroupAssignmentRepository.class),
            mock(LecturerRubricAssignmentRepository.class),
            mock(LecturerStudentAssignmentRepository.class),
            mock(StudentAssessmentAssignmentRepository.class),
            mock(LecturerAssessmentService.class),
            mock(MarkService.class),
            markRepository,
            commentRepository,
            logRepository);

    private Assessment assessment;
    private Rubric presentation;   // group rubric
    private Rubric report;         // individual rubric
    private SubRubric clarity;
    private Rating good;
    private Group teamA;
    private Student ali;
    private Student bella;
    private Student chen;
    private Lecturer drLim;

    @BeforeEach
    void setUp() {
        assessment = new Assessment();
        assessment.setId(7L);
        assessment.setTitle("Final presentation");

        presentation = new Rubric();
        presentation.setId(11L);
        presentation.setName("Presentation");
        presentation.setAssessmentTypes("Group Assessment");
        presentation.setMarks(new BigDecimal("20"));

        report = new Rubric();
        report.setId(12L);
        report.setName("Report");
        report.setAssessmentTypes("Individual Assessment");
        report.setMarks(new BigDecimal("10"));

        clarity = new SubRubric();
        clarity.setId(21L);
        clarity.setName("Clarity");
        clarity.setRubric(presentation);
        clarity.setMarks(new BigDecimal("20"));

        good = new Rating();
        good.setId(31L);
        good.setName("Good");
        good.setMarks(new BigDecimal("15"));

        teamA = new Group();
        teamA.setId(1L);
        teamA.setGroupName("Team A");

        ali = student(101L, "Ali", teamA);
        bella = student(102L, "Bella", teamA);
        chen = student(103L, "Chen", null);
        teamA.setStudents(new ArrayList<>(List.of(ali, bella)));

        drLim = new Lecturer();
        drLim.setId(5L);
        drLim.setUsername("Dr Lim");
    }

    private static Student student(Long id, String name, Group group) {
        Student s = new Student();
        s.setId(id);
        s.setUsername(name);
        s.setGroup(group);
        return s;
    }

    private Mark lecturerMark(Student target, Rubric rubric, String type) {
        Mark m = baseMark(target, target, rubric, type);
        m.setLecturer(drLim);
        m.setComments("LECTURER:" + drLim.getId());
        return m;
    }

    private Mark studentMark(Student evaluator, Student evaluated, Rubric rubric, String type) {
        return baseMark(evaluator, evaluated, rubric, type);
    }

    private Mark baseMark(Student evaluator, Student evaluated, Rubric rubric, String type) {
        Mark m = new Mark();
        m.setEvaluatorStudent(evaluator);
        m.setEvaluatedStudent(evaluated);
        m.setAssessment(assessment);
        m.setRubric(rubric);
        m.setSubRubric(rubric == presentation ? clarity : null);
        m.setRating(good);
        m.setMarkValue(15.0);
        m.setAssessmentType(type);
        m.setStatus(Mark.SubmissionStatus.FINAL);
        return m;
    }

    private void finalMarks(Mark... marks) {
        when(markRepository.findByAssessment(assessment)).thenReturn(List.of(marks));
    }

    private AssessmentComment comment(AssessmentComment.EvaluatorType type, Long evaluatorId, Student about,
                                      Long rubricId, String rubricType, String label, String text) {
        AssessmentComment c = new AssessmentComment();
        c.setEvaluatorType(type);
        c.setEvaluatorId(evaluatorId);
        c.setEvaluatedStudent(about);
        c.setAssessment(assessment);
        c.setRubricId(rubricId);
        c.setRubricAssessmentType(rubricType);
        c.setCommentLabel(label);
        c.setCommentText(text);
        return c;
    }

    // --- listing ---------------------------------------------------------------------------------------------

    @Test
    void anAssessorEvaluationShowsWhoMarkedWhomWithTheRatingsAndTotal() {
        finalMarks(lecturerMark(chen, report, "Individual Assessment"));

        List<EvaluationRow> rows = service.findEvaluations(assessment, null, null, null);

        assertEquals(1, rows.size());
        EvaluationRow row = rows.get(0);
        assertEquals("LECTURER", row.evaluatorKind());
        assertEquals("Dr Lim", row.evaluatorName());
        assertEquals("Chen", row.targetName());
        assertEquals("Report", row.rubricName());
        assertEquals(15.0, row.totalMarks());
        assertEquals(10.0, row.maxMarks());
        assertEquals(1, row.lines().size());
        assertEquals("Good", row.lines().get(0).rating());
    }

    @Test
    void aStudentPeerEvaluationIsListedUnderTheStudentWhoGaveIt() {
        finalMarks(studentMark(ali, bella, report, "Peer Assessment"));

        EvaluationRow row = service.findEvaluations(assessment, null, null, null).get(0);

        assertEquals("STUDENT", row.evaluatorKind());
        assertEquals("Ali", row.evaluatorName());
        assertEquals("Bella", row.targetName());
        assertEquals("Peer Assessment", row.assessmentType());
    }

    @Test
    void anAssessorsGroupEvaluationIsOneRowForTheWholeGroupNotOnePerMember() {
        finalMarks(lecturerMark(ali, presentation, "Group Assessment"), lecturerMark(bella, presentation, "Group Assessment"));

        List<EvaluationRow> rows = service.findEvaluations(assessment, null, null, null);

        assertEquals(1, rows.size());
        assertEquals("Team A", rows.get(0).targetName());
        assertEquals(List.of(101L, 102L), rows.get(0).targetIds().stream().sorted().toList());
        assertEquals(1, rows.get(0).lines().size());
    }

    @Test
    void filtersNarrowByGroupStudentAndRubric() {
        finalMarks(lecturerMark(ali, presentation, "Group Assessment"), lecturerMark(bella, presentation, "Group Assessment"),
                lecturerMark(chen, report, "Individual Assessment"), studentMark(ali, bella, report, "Peer Assessment"));

        assertEquals(2, service.findEvaluations(assessment, 1L, null, null).size());   // Team A: group row + peer row
        assertEquals(1, service.findEvaluations(assessment, null, 103L, null).size()); // Chen only
        assertEquals(1, service.findEvaluations(assessment, null, null, 11L).size());  // Presentation only
        assertEquals(0, service.findEvaluations(assessment, null, 103L, 11L).size());
    }

    @Test
    void rubricSpecificCommentsAreShownWithTheEvaluation() {
        finalMarks(lecturerMark(chen, report, "Individual Assessment"));
        when(commentRepository.findByAssessment(assessment)).thenReturn(List.of(
                comment(AssessmentComment.EvaluatorType.LECTURER, 5L, chen, 12L, "Individual Assessment", "Strengths", "Clear structure"),
                comment(AssessmentComment.EvaluatorType.LECTURER, 5L, chen, 99L, "Individual Assessment", "Other", "Other rubric"),
                comment(AssessmentComment.EvaluatorType.STUDENT, 5L, chen, 12L, "Individual Assessment", "Peer", "Different evaluator")));

        EvaluationRow row = service.findEvaluations(assessment, null, null, null).get(0);

        assertEquals(1, row.comments().size());
        assertEquals("Clear structure", row.comments().get(0).text());
    }

    // --- delete ----------------------------------------------------------------------------------------------

    @Test
    void deleteRemovesTheMarksAndRubricCommentsAndWritesAnAuditRecord() {
        Mark mark = lecturerMark(chen, report, "Individual Assessment");
        finalMarks(mark);
        AssessmentComment mine = comment(AssessmentComment.EvaluatorType.LECTURER, 5L, chen, 12L, "Individual Assessment", "Strengths", "Clear structure");
        AssessmentComment otherRubric = comment(AssessmentComment.EvaluatorType.LECTURER, 5L, chen, 99L, "Individual Assessment", "Other", "Keep me");
        when(commentRepository.findByAssessment(assessment)).thenReturn(List.of(mine, otherRubric));

        int removed = service.deleteEvaluation(assessment, "LECTURER", 5L, 12L, "Individual Assessment", List.of(103L),
                "Entered against the wrong student", "admin1");

        assertEquals(1, removed);
        verify(markRepository).deleteAll(List.of(mark));
        ArgumentCaptor<List<AssessmentComment>> deleted = ArgumentCaptor.forClass(List.class);
        verify(commentRepository).deleteAll(deleted.capture());
        assertEquals(List.of(mine), deleted.getValue());

        ArgumentCaptor<EvaluationDeletionLog> log = ArgumentCaptor.forClass(EvaluationDeletionLog.class);
        verify(logRepository).save(log.capture());
        assertEquals("admin1", log.getValue().getDeletedBy());
        assertEquals("Entered against the wrong student", log.getValue().getReason());
        assertEquals("Dr Lim", log.getValue().getEvaluatorName());
        assertEquals("Chen", log.getValue().getEvaluatedNames());
        assertEquals(7L, log.getValue().getAssessmentId());
        assertTrue(log.getValue().getSnapshot().contains("Good"));
        assertTrue(log.getValue().getSnapshot().contains("Clear structure"));
    }

    @Test
    void deleteLeavesOtherEvaluationsByTheSameAssessorAlone() {
        Mark reportMark = lecturerMark(chen, report, "Individual Assessment");
        Mark otherStudent = lecturerMark(ali, report, "Individual Assessment");
        finalMarks(reportMark, otherStudent);

        service.deleteEvaluation(assessment, "LECTURER", 5L, 12L, "Individual Assessment", List.of(103L), "Wrong student", "admin1");

        verify(markRepository).deleteAll(List.of(reportMark));
    }

    @Test
    void deletingAGroupEvaluationRemovesTheMarksOfEveryMember() {
        Mark a = lecturerMark(ali, presentation, "Group Assessment");
        Mark b = lecturerMark(bella, presentation, "Group Assessment");
        finalMarks(a, b);

        int removed = service.deleteEvaluation(assessment, "LECTURER", 5L, 11L, "Group Assessment", List.of(101L, 102L), "Re-run", "admin1");

        assertEquals(2, removed);
        verify(markRepository).deleteAll(List.of(a, b));
    }

    @Test
    void generalCommentsStayWhileAnotherMarkOfThatTypeRemainsForTheStudent() {
        Mark reportMark = lecturerMark(chen, report, "Individual Assessment");
        Mark sibling = lecturerMark(chen, report, "Individual Assessment");
        sibling.setRubric(presentation);   // a second Individual Assessment rubric, still marked
        finalMarks(reportMark, sibling);
        AssessmentComment general = comment(AssessmentComment.EvaluatorType.LECTURER, 5L, chen, null, "Individual Assessment", "Overall", "Good overall");
        when(commentRepository.findByAssessment(assessment)).thenReturn(List.of(general));

        service.deleteEvaluation(assessment, "LECTURER", 5L, 12L, "Individual Assessment", List.of(103L), "Redo", "admin1");

        verify(commentRepository, never()).deleteAll(List.of(general));
    }

    @Test
    void generalCommentsGoWhenTheLastMarkOfThatTypeIsDeleted() {
        Mark reportMark = lecturerMark(chen, report, "Individual Assessment");
        finalMarks(reportMark);
        AssessmentComment general = comment(AssessmentComment.EvaluatorType.LECTURER, 5L, chen, null, "Individual Assessment", "Overall", "Good overall");
        when(commentRepository.findByAssessment(assessment)).thenReturn(List.of(general));

        service.deleteEvaluation(assessment, "LECTURER", 5L, 12L, "Individual Assessment", List.of(103L), "Redo", "admin1");

        verify(commentRepository).deleteAll(List.of(general));
    }

    @Test
    void deleteNeedsAReason() {
        finalMarks(lecturerMark(chen, report, "Individual Assessment"));

        assertThrows(IllegalArgumentException.class, () ->
                service.deleteEvaluation(assessment, "LECTURER", 5L, 12L, "Individual Assessment", List.of(103L), "  ", "admin1"));
        verify(markRepository, never()).deleteAll(anyList());
        verify(logRepository, never()).save(any());
    }

    @Test
    void deleteOfSomethingThatIsAlreadyGoneSaysSoAndChangesNothing() {
        finalMarks();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                service.deleteEvaluation(assessment, "LECTURER", 5L, 12L, "Individual Assessment", List.of(103L), "Cleanup", "admin1"));

        assertTrue(e.getMessage().toLowerCase().contains("no longer"));
        verify(logRepository, never()).save(any());
    }

    @Test
    void unknownEvaluatorKindIsRejected() {
        finalMarks(lecturerMark(chen, report, "Individual Assessment"));

        assertThrows(IllegalArgumentException.class, () ->
                service.deleteEvaluation(assessment, "ROBOT", 5L, 12L, "Individual Assessment", List.of(103L), "Reason", "admin1"));
    }

    @Test
    void submittedAndOldStatuslessEvaluationsAreListedNotOnlyFinalOnes() {
        Mark submitted = studentMark(ali, chen, report, "Peer Assessment");
        submitted.setStatus(Mark.SubmissionStatus.SUBMITTED);
        Mark statusless = studentMark(bella, chen, report, "Peer Assessment");
        statusless.setStatus(null);
        Mark draft = studentMark(chen, chen, report, "Self Assessment");
        draft.setStatus(Mark.SubmissionStatus.DRAFT);
        finalMarks(submitted, statusless, draft);

        List<EvaluationRow> rows = service.findEvaluations(assessment, null, null, null);

        assertEquals(2, rows.size(), "the draft is not a finished evaluation");
        ProgressTrackingService.MarkCounts counts = service.markCounts(assessment);
        assertEquals(2, counts.finished());
        assertEquals(1, counts.drafts());
    }
}
