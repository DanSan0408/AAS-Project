package com.capstone.adproject.controller;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.capstone.adproject.dto.ai.AiModerationDtos;
import com.capstone.adproject.dto.analytics.LecturerComparisonData;
import com.capstone.adproject.dto.analytics.LecturerComparisonData.Criterion;
import com.capstone.adproject.dto.analytics.LecturerComparisonData.Score;
import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.AssessmentComment;
import com.capstone.adproject.model.Group;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.service.AiServiceClient;
import com.capstone.adproject.service.AssessmentCommentService;
import com.capstone.adproject.service.AssessmentService;
import com.capstone.adproject.service.CourseScopeService;
import com.capstone.adproject.service.LecturerAnalyticsService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** What the moderation briefing is sent: the rating step of each criterion, and assessors' comments tied to the work they are about. */
class ModerationBriefingRequestTest {

    private final CourseScopeService scope = mock(CourseScopeService.class);
    private final LecturerAnalyticsService analytics = mock(LecturerAnalyticsService.class);
    private final AiServiceClient ai = mock(AiServiceClient.class);
    private final AssessmentService assessmentService = mock(AssessmentService.class);
    private final AssessmentCommentService commentService = mock(AssessmentCommentService.class);
    private final LecturerAnalyticsController controller = new LecturerAnalyticsController(scope, analytics, ai, assessmentService, commentService);

    private static Student student(long id, Group group) {
        Student s = new Student();
        s.setId(id);
        s.setGroup(group);
        return s;
    }

    private static AssessmentComment comment(Student about, String text) {
        AssessmentComment c = new AssessmentComment();
        c.setEvaluatedStudent(about);
        c.setCommentText(text);
        c.setEvaluatorName("Dr Lim");
        c.setCommentLabel("Strengths");
        return c;
    }

    @Test
    void criteriaCarryTheirStepAndCommentsAreTiedToTheSameTargetNamesAsTheScores() {
        Group team = new Group();
        team.setId(7L);
        Student inTeam = student(1, team);
        Student individual = student(2, null);
        Student unrelated = student(3, null);
        LecturerComparisonData data = new LecturerComparisonData(5L, "Final", List.of(),
                List.of(new Criterion("s1", 1L, "Report", "Clarity", 1, "GROUP", 5.0, 1.0)),
                List.of(new Score(10L, "Dr Lim", 7L, "Team Alpha", null, null, "GROUP:7", "Team Alpha", "s1", 4.0, "FINAL", null),
                        new Score(10L, "Dr Lim", 7L, "Team Alpha", 2L, "Bella", "IND:2", "Bella", "s1", 3.0, "FINAL", null)), 0);
        when(scope.getActiveCourseIdForCurrentUser()).thenReturn(1L);
        when(analytics.comparison(1L, 5L)).thenReturn(data);
        Assessment assessment = new Assessment();
        assessment.setId(5L);
        when(assessmentService.getAssessmentById(5L)).thenReturn(Optional.of(assessment));
        when(commentService.getAssessorCommentsForBriefing(assessment)).thenReturn(List.of(
                comment(inTeam, "Team presentation was uneven."), comment(individual, "Report needs a clearer argument."),
                comment(unrelated, "Not part of the compared work.")));

        controller.moderationBriefing(5L);

        ArgumentCaptor<AiModerationDtos.BriefingRequest> sent = ArgumentCaptor.forClass(AiModerationDtos.BriefingRequest.class);
        verify(ai).generateModerationBriefing(sent.capture());
        assertEquals(1.0, sent.getValue().criteria().get(0).get("step"));
        assertEquals(2, sent.getValue().comments().size(), "a comment that cannot be tied to compared work is left out");
        assertEquals("Team Alpha", sent.getValue().comments().get(0).targetName());
        assertEquals("Bella", sent.getValue().comments().get(1).targetName());
        assertEquals("Dr Lim", sent.getValue().comments().get(0).lecturerName());
    }

    @Test
    void withoutTheCommentCollaboratorsTheBriefingStillWorksWithNoComments() {
        LecturerAnalyticsController bare = new LecturerAnalyticsController(scope, analytics, ai, null, null);
        LecturerComparisonData data = new LecturerComparisonData(5L, "Final", List.of(), List.of(), List.of(), 0);
        when(scope.getActiveCourseIdForCurrentUser()).thenReturn(1L);
        when(analytics.comparison(1L, 5L)).thenReturn(data);

        bare.moderationBriefing(5L);

        ArgumentCaptor<AiModerationDtos.BriefingRequest> sent = ArgumentCaptor.forClass(AiModerationDtos.BriefingRequest.class);
        verify(ai).generateModerationBriefing(sent.capture());
        assertTrue(sent.getValue().comments().isEmpty());
    }

    @Test
    void tooManyRequestsGetAFriendlyWaitMessage() {
        when(scope.getActiveCourseIdForCurrentUser()).thenReturn(1L);
        when(analytics.comparison(1L, 5L)).thenReturn(new LecturerComparisonData(5L, "Final", List.of(), List.of(), List.of(), 0));
        when(ai.generateModerationBriefing(any())).thenThrow(new AiServiceClient.RateLimitedException());

        var response = controller.moderationBriefing(5L);

        assertEquals(429, response.getStatusCode().value());
    }
}
