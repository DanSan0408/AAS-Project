package com.capstone.adproject.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.capstone.adproject.model.*;
import com.capstone.adproject.repositories.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LecturerAnalyticsServiceTest {
    @Mock AssessmentRepository assessments;
    @Mock MarkRepository marks;
    @Mock GroupRepository groups;
    @Mock LecturerRepository lecturers;
    LecturerAnalyticsService service;
    Course course;
    Assessment assessment;
    Rubric rubric;

    @BeforeEach void setup() {
        service = new LecturerAnalyticsService(assessments, marks, groups, lecturers);
        course = new Course(); course.setId(10L);
        assessment = new Assessment(); assessment.setId(20L); assessment.setCourse(course); assessment.setTitle("Design review");
        rubric = new Rubric(); rubric.setId(30L); rubric.setName("Technical quality"); rubric.setAssessmentTypes("Group Assessment"); rubric.setMarks(BigDecimal.TEN); rubric.setClo(1);
        assessment.setRubrics(List.of(rubric));
        Group team = new Group("Team A"); team.setId(40L); team.setCourse(course);
        when(assessments.findById(20L)).thenReturn(Optional.of(assessment));
        when(groups.findByCourseId(10L)).thenReturn(List.of(team));
    }
    private LecturerAnalyticsMark row(long id, Long lecturerId, long student, double score) {
        LecturerAnalyticsMark r = mock(LecturerAnalyticsMark.class);
        when(r.getId()).thenReturn(id); when(r.getLecturerId()).thenReturn(lecturerId);
        when(r.getLecturerName()).thenReturn(lecturerId == null ? null : "Lecturer " + lecturerId);
        when(r.getStudentId()).thenReturn(student); when(r.getStudentName()).thenReturn("Student " + student);
        when(r.getTeamId()).thenReturn(40L); when(r.getTeamName()).thenReturn("Team A");
        when(r.getSubRubricId()).thenReturn(null); when(r.getRubricId()).thenReturn(30L); when(r.getAssessmentType()).thenReturn("Group Assessment");
        when(r.getEarned()).thenReturn(score); when(r.getStatus()).thenReturn(Mark.SubmissionStatus.FINAL);
        when(r.getSubmittedAt()).thenReturn(LocalDateTime.of(2026, 10, 28, 10, 0));
        return r;
    }
    private void rows(LecturerAnalyticsMark... rows) {
        when(marks.findLecturerAnalyticsMarks(eq(20L), eq(10L), anyList())).thenReturn(List.of(rows));
    }
    @Test void teamCopiesCountOnceAndLatestWins() {
        rows(row(1, 1L, 101, 4), row(2, 1L, 102, 8), row(3, 1L, 103, 8), row(4, 2L, 101, 6), row(5, 2L, 102, 6));
        var data = service.comparison(10L, 20L);
        assertEquals(2, data.scores().size());
        assertEquals(8, data.scores().get(0).earned());
        assertEquals("GROUP:40", data.scores().get(0).targetId());
        assertNull(data.scores().get(0).studentId());
        assertEquals(10, data.criteria().get(0).possible());
        assertEquals(0, data.excludedRows());
    }
    @Test void individualEvaluationsStaySeparateWithinOneTeam() {
        rubric.setAssessmentTypes("Individual Assessment");
        var a = row(1, 1L, 101, 4); var b = row(2, 1L, 102, 8);
        when(a.getAssessmentType()).thenReturn("Individual Assessment"); when(b.getAssessmentType()).thenReturn("Individual Assessment");
        rows(a, b);
        var data = service.comparison(10L, 20L);
        assertEquals(2, data.scores().size());
        assertNotEquals(data.scores().get(0).targetId(), data.scores().get(1).targetId());
        assertNotNull(data.scores().get(0).studentId());
    }
    @Test void draftsPeersInvalidScoresAndForeignTeamsAreExcluded() {
        var draft = row(1, 1L, 101, 4); when(draft.getStatus()).thenReturn(Mark.SubmissionStatus.DRAFT);
        var peer = row(2, null, 101, 8);
        var invalid = row(3, 1L, 101, Double.NaN);
        var tooHigh = row(4, 1L, 101, 11);
        var foreign = row(5, 1L, 101, 5); when(foreign.getTeamId()).thenReturn(999L);
        rows(draft, peer, invalid, tooHigh, foreign);
        var data = service.comparison(10L, 20L);
        assertTrue(data.scores().isEmpty()); assertEquals(5, data.excludedRows());
    }
    @Test void crossCourseAssessmentNeverLoadsMarksOrTeamNames() {
        assertThrows(IllegalArgumentException.class, () -> service.comparison(999L, 20L));
        verifyNoInteractions(marks, groups, lecturers);
    }
    @Test void missingActiveCourseNeverQueriesAnyRepository() {
        assertThrows(IllegalArgumentException.class, () -> service.comparison(null, 20L));
        assertTrue(service.assessmentsForCourse(null).isEmpty());
        verifyNoInteractions(assessments, marks, groups, lecturers);
    }
    @Test void legacyLecturerTagIsResolvedWithoutExposingTheEntity() {
        var mark = row(1, null, 101, 5); when(mark.getSourceTag()).thenReturn("LECTURER:9");
        Lecturer lecturer = new Lecturer(); lecturer.setId(9L); lecturer.setUsername("Dr Legacy"); lecturer.setPassword("never-export");
        when(lecturers.findAllById(List.of(9L))).thenReturn(List.of(lecturer)); rows(mark);
        var data = service.comparison(10L, 20L);
        assertEquals(9L, data.scores().get(0).lecturerId()); assertEquals("Dr Legacy", data.scores().get(0).lecturerName());
        assertFalse(data.toString().contains("never-export"));
    }
    @Test void subcriteriaUseTheirOwnMaximumAndRejectObsoleteParentScores() {
        SubRubric sub = new SubRubric(); sub.setId(50L); sub.setName("Evidence"); sub.setMarks(BigDecimal.valueOf(4));
        rubric.setSubRubrics(List.of(sub));
        var mark = row(1, 1L, 101, 3); when(mark.getSubRubricId()).thenReturn(50L);
        rows(mark, row(2, 1L, 101, 8));
        var data = service.comparison(10L, 20L);
        assertEquals(1, data.scores().size()); assertEquals(4, data.criteria().get(0).possible());
        assertEquals("s50", data.scores().get(0).criterionId()); assertEquals(1, data.excludedRows());
    }
    @Test void invalidCriterionMaximumProducesNoArtificialZeroScores() {
        rubric.setMarks(BigDecimal.ZERO); rows(row(1, 1L, 101, 0));
        var data = service.comparison(10L, 20L);
        assertTrue(data.criteria().isEmpty()); assertTrue(data.scores().isEmpty()); assertEquals(1, data.excludedRows());
    }
    @Test void directRatingsUseTheRatingScaleInsteadOfTheRubricWeight() {
        rubric.setMarks(BigDecimal.valueOf(20));
        Rating top = new Rating(); top.setMarks(BigDecimal.valueOf(5));
        rubric.setRatings(List.of(top)); rows(row(1, 1L, 101, 4));
        var data = service.comparison(10L, 20L);
        assertEquals(5, data.criteria().get(0).possible());
        assertEquals(80, data.scores().get(0).earned() / data.criteria().get(0).possible() * 100);
    }
    @Test void invalidConfiguredRatingScaleDoesNotFallBackToAnUnrelatedWeight() {
        Rating invalid = new Rating(); invalid.setMarks(BigDecimal.ZERO);
        rubric.setRatings(List.of(invalid)); rows(row(1, 1L, 101, 0));
        var data = service.comparison(10L, 20L);
        assertTrue(data.scores().isEmpty()); assertEquals(1, data.excludedRows());
    }
}
