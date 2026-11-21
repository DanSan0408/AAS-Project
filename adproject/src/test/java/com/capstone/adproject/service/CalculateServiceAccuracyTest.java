package com.capstone.adproject.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.capstone.adproject.dto.StudentAssessmentDataDto;
import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Mark;
import com.capstone.adproject.model.Rating;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.SubRubric;
import com.capstone.adproject.repositories.AssessmentCommentRepository;
import com.capstone.adproject.repositories.FactorWeightageRepository;
import com.capstone.adproject.repositories.LecturerGroupAssignmentRepository;
import com.capstone.adproject.repositories.MarkRepository;
import com.capstone.adproject.repositories.StudentResultOverrideRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** What counts as a mark, and how evaluators are told apart: the rules every result and cohort metric rests on. */
@ExtendWith(MockitoExtension.class)
class CalculateServiceAccuracyTest {

    @Mock private MarkRepository markRepository;
    @Mock private LecturerGroupAssignmentRepository lecturerAssignmentRepository;
    @Mock private AssessmentCommentRepository commentRepository;
    @Mock private StudentResultOverrideRepository overrideRepository;
    @Mock private FactorWeightageRepository factorWeightageRepository;

    @InjectMocks
    private CalculateService calculateService;

    private Assessment assessment;
    private Rubric rubric;
    private SubRubric criterion;
    private Student student;

    @BeforeEach
    void setUp() {
        assessment = new Assessment();
        assessment.setId(1L);
        assessment.setRubrics(new ArrayList<>());

        rubric = new Rubric();
        rubric.setId(10L);
        rubric.setName("Report");
        rubric.setMarks(new BigDecimal("10"));
        rubric.setClo(1);
        rubric.setAssessmentTypes("Individual Assessment");
        rubric.setAssessment(assessment);

        criterion = new SubRubric();
        criterion.setId(100L);
        criterion.setRubric(rubric);
        criterion.setRatings(new ArrayList<>(List.of(rating(1, 1), rating(2, 3), rating(3, 5))));
        rubric.setSubRubrics(new ArrayList<>(List.of(criterion)));
        rubric.setRatings(new ArrayList<>());
        assessment.getRubrics().add(rubric);

        student = new Student();
        student.setId(7L);
    }

    private Rating rating(long id, int marks) {
        Rating r = new Rating();
        r.setId(id);
        r.setMarks(new BigDecimal(marks));
        r.setSubRubric(criterion);
        return r;
    }

    private Mark mark(Lecturer by, Student byStudent, int marks, Mark.SubmissionStatus status) {
        Mark m = new Mark();
        m.setEvaluatedStudent(student);
        m.setEvaluatorStudent(byStudent != null ? byStudent : student);
        m.setLecturer(by);
        m.setAssessment(assessment);
        m.setRubric(rubric);
        m.setSubRubric(criterion);
        Rating r = new Rating();
        r.setMarks(new BigDecimal(marks));
        m.setRating(r);
        m.setStatus(status);
        return m;
    }

    private static Lecturer lecturer(long id) {
        Lecturer l = new Lecturer();
        l.setId(id);
        return l;
    }

    private static Student studentWithId(long id) {
        Student s = new Student();
        s.setId(id);
        return s;
    }

    private double rubricMark(List<Mark> marks) {
        StudentAssessmentDataDto data = calculateService.calculateStudentAssessmentData(student, assessment, 1.0, marks);
        return data.getRubricCalculations().get(10L).getEvaluatedRubricMark();
    }

    @Test
    void unfinishedDraftMarksDoNotChangeAStudentsMark() {
        Lecturer dr = lecturer(5);
        double withDraft = rubricMark(List.of(
                mark(dr, null, 5, Mark.SubmissionStatus.FINAL),
                mark(dr, null, 1, Mark.SubmissionStatus.DRAFT)));

        assertEquals(10.0, withDraft, 0.001, "only the finished 5/5 counts, so the rubric is 10/10");
    }

    @Test
    void aStudentWhoOnlyHasDraftMarksIsNotEvaluatedYet() {
        StudentAssessmentDataDto data = calculateService.calculateStudentAssessmentData(student, assessment, 1.0,
                List.of(mark(lecturer(5), null, 5, Mark.SubmissionStatus.DRAFT)));

        assertFalse(data.isEvaluated());
        assertTrue(calculateService.calculateStudentAssessmentData(student, assessment, 1.0,
                List.of(mark(lecturer(5), null, 5, Mark.SubmissionStatus.FINAL))).isEvaluated());
    }

    @Test
    void legacyMarksWithNoStatusStillCount() {
        Mark legacy = mark(lecturer(5), null, 5, Mark.SubmissionStatus.FINAL);
        legacy.setStatus(null);

        assertEquals(10.0, rubricMark(List.of(legacy)), 0.001);
    }

    @Test
    void assessorMarksAreNotDilutedByStudentRatingsOnTheSameRubric() {
        // Lecturer #5 gives 5/5; student #5 (a different person with the same number) gives 1/5.
        double mark = rubricMark(List.of(
                mark(lecturer(5), null, 5, Mark.SubmissionStatus.FINAL),
                mark(null, studentWithId(5), 1, Mark.SubmissionStatus.FINAL)));

        assertEquals(10.0, mark, 0.001, "assessors decide an assessed rubric; the student rating is not averaged in");
    }

    @Test
    void aRubricRatedOnlyByStudentsStillUsesTheirRatings() {
        double mark = rubricMark(List.of(
                mark(null, studentWithId(21), 5, Mark.SubmissionStatus.FINAL),
                mark(null, studentWithId(22), 1, Mark.SubmissionStatus.FINAL)));

        assertEquals(6.0, mark, 0.001, "(5 + 1) / 2 / 5 of 10 marks");
    }

    @Test
    void aMissingTeamworkComponentIsLeftOutNotCountedAsZero() {
        // Weights 50/50, data for the first assessment only (factor 1.2): the factor is 1.2, not 0.6.
        double factor = CalculateService.combineWeightedFactors(List.of(1.2, 0.0), List.of(0.5, 0.5));

        assertEquals(1.2, factor, 0.0001);
    }

    @Test
    void whenEveryComponentHasDataTheWeightedSumIsUnchanged() {
        assertEquals(0.5 * 1.2 + 0.5 * 0.8, CalculateService.combineWeightedFactors(List.of(1.2, 0.8), List.of(0.5, 0.5)), 0.0001);
        assertEquals(0.3 * 1.0 + 0.5 * 1.1, CalculateService.combineWeightedFactors(List.of(1.0, 1.1), List.of(0.3, 0.5)), 0.0001);
    }

    @Test
    void noTeamworkDataAtAllGivesNoFactor() {
        assertEquals(0.0, CalculateService.combineWeightedFactors(List.of(0.0, 0.0), List.of(0.5, 0.5)), 0.0001);
        assertEquals(0.0, CalculateService.combineWeightedFactors(List.of(), List.of()), 0.0001);
    }

    @Test
    void aStudentsOwnRatingIsNotPartOfWhatTheirTeammatesGaveThem() {
        Student member = studentWithId(7);
        Mark fromSelf = mark(null, member, 5, Mark.SubmissionStatus.FINAL);
        Mark fromPeer = mark(null, studentWithId(8), 3, Mark.SubmissionStatus.FINAL);

        List<Mark> received = CalculateService.ratingsFromOthers(List.of(fromSelf, fromPeer), member);

        assertEquals(List.of(fromPeer), received);
    }

    // --- What the AI audit may read ------------------------------------------------------------------------------------

    private com.capstone.adproject.model.AssessmentComment comment(com.capstone.adproject.model.AssessmentComment.EvaluatorType type,
            com.capstone.adproject.model.AssessmentComment.ApprovalStatus approval, Long evaluatorId, String text) {
        com.capstone.adproject.model.AssessmentComment c = new com.capstone.adproject.model.AssessmentComment();
        c.setEvaluatorType(type);
        c.setApprovalStatus(approval);
        c.setEvaluatorId(evaluatorId);
        c.setCommentText(text);
        c.setEvaluatedStudent(student);
        c.setAssessment(assessment);
        return c;
    }

    @Test
    void theAiMayOnlyReadApprovedCommentsFromTeammates() {
        var approved = com.capstone.adproject.model.AssessmentComment.ApprovalStatus.APPROVED;
        var teammate = com.capstone.adproject.model.AssessmentComment.EvaluatorType.STUDENT;
        var fine = comment(teammate, approved, 8L, "Reliable and on time.");
        when(commentRepository.findByEvaluatedStudentAndAssessment(student, assessment)).thenReturn(List.of(
                fine,
                comment(teammate, com.capstone.adproject.model.AssessmentComment.ApprovalStatus.PENDING, 8L, "Not yet moderated."),
                comment(teammate, com.capstone.adproject.model.AssessmentComment.ApprovalStatus.REJECTED, 8L, "Rejected as abusive."),
                comment(com.capstone.adproject.model.AssessmentComment.EvaluatorType.LECTURER, approved, 5L, "An assessor's comment."),
                comment(teammate, approved, 7L, "The student's own comment about themselves."),
                comment(teammate, approved, 8L, "   ")));

        List<com.capstone.adproject.model.AssessmentComment> readable = calculateService.getApprovedPeerComments(student, assessment);

        assertEquals(List.of(fine), readable);
    }
}
