package com.capstone.adproject.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.capstone.adproject.dto.StudentAssessmentDataDto;
import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Lecturer;
import com.capstone.adproject.model.Mark;
import com.capstone.adproject.model.Rating;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.SubRubric;
import com.capstone.adproject.service.CohortMetrics.CriterionSample;
import com.capstone.adproject.service.CohortMetrics.StudentResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CohortInputsTest {

    private Assessment assessment;
    private Rubric groupRubric;
    private SubRubric criterion;

    private static Rating level(int marks) {
        Rating r = new Rating();
        r.setMarks(new BigDecimal(marks));
        return r;
    }

    @BeforeEach
    void setUp() {
        assessment = new Assessment();
        assessment.setId(1L);
        groupRubric = new Rubric();
        groupRubric.setId(10L);
        groupRubric.setName("Presentation");
        groupRubric.setClo(2);
        groupRubric.setMarks(new BigDecimal("20"));
        groupRubric.setAssessmentTypes("Group Assessment");
        criterion = new SubRubric();
        criterion.setId(100L);
        criterion.setName("Clarity");
        criterion.setRatings(new ArrayList<>(List.of(level(1), level(3), level(5))));
        groupRubric.setSubRubrics(new ArrayList<>(List.of(criterion)));
        groupRubric.setRatings(new ArrayList<>());
        assessment.setRubrics(new ArrayList<>(List.of(groupRubric)));
    }

    private Mark mark(Lecturer by, Student evaluator, Student about, int value) {
        Mark m = new Mark();
        m.setRubric(groupRubric);
        m.setSubRubric(criterion);
        m.setLecturer(by);
        m.setEvaluatorStudent(evaluator != null ? evaluator : about);
        m.setEvaluatedStudent(about);
        m.setRating(level(value));
        return m;
    }

    private static Student student(long id) {
        Student s = new Student();
        s.setId(id);
        return s;
    }

    private static Lecturer lecturer(long id) {
        Lecturer l = new Lecturer();
        l.setId(id);
        return l;
    }

    @Test
    void marksOnOfferAreSummedPerLearningOutcome() {
        Rubric second = new Rubric();
        second.setClo(2);
        second.setMarks(new BigDecimal("10"));
        Rubric third = new Rubric();
        third.setClo(3);
        third.setMarks(new BigDecimal("5"));
        assessment.getRubrics().addAll(List.of(second, third));

        Map<Integer, Double> available = CohortInputs.cloAvailable(List.of(assessment));

        assertEquals(30.0, available.get(2), 0.001);
        assertEquals(5.0, available.get(3), 0.001);
    }

    @Test
    void aGroupRubricStoredPerMemberIsCountedOncePerAssessorAndTeam() {
        Student a = student(1), b = student(2), c = student(3);
        Lecturer dr = lecturer(5);
        // The same assessor rating copied to each of the three team members.
        Map<Long, Map<Long, List<Mark>>> marks = Map.of(1L, Map.of(
                1L, List.of(mark(dr, null, a, 5)), 2L, List.of(mark(dr, null, b, 5)), 3L, List.of(mark(dr, null, c, 5))));

        List<CriterionSample> samples = CohortInputs.criterionSamples(List.of(assessment), marks, Map.of(1L, 9L, 2L, 9L, 3L, 9L));

        assertEquals(1, samples.size(), "one team, one assessor, one criterion = one rating");
        assertEquals(1.0, samples.get(0).fraction(), 0.001);
        assertTrue(samples.get(0).top());
        assertFalse(samples.get(0).bottom());
        assertEquals("Presentation - Clarity", samples.get(0).label());
    }

    @Test
    void aTwoAssessorRatingsOnTheSameTeamBothCount() {
        Student a = student(1);
        Map<Long, Map<Long, List<Mark>>> marks = Map.of(1L, Map.of(1L, List.of(mark(lecturer(5), null, a, 5), mark(lecturer(6), null, a, 1))));

        List<CriterionSample> samples = CohortInputs.criterionSamples(List.of(assessment), marks, Map.of(1L, 9L));

        assertEquals(2, samples.size());
        assertTrue(samples.stream().anyMatch(CriterionSample::bottom));
    }

    @Test
    void studentRatingsAreIgnoredWhereAnAssessorMarkedTheRubric() {
        Student a = student(1);
        Map<Long, Map<Long, List<Mark>>> marks = Map.of(1L, Map.of(1L, List.of(
                mark(lecturer(5), null, a, 5), mark(null, student(2), a, 1))));

        List<CriterionSample> samples = CohortInputs.criterionSamples(List.of(assessment), marks, Map.of());

        assertEquals(1, samples.size());
        assertEquals(1.0, samples.get(0).fraction(), 0.001);
    }

    @Test
    void resultsCarryTheFinalMarkOverrideAndSummedOutcomeMarks() {
        Student s = student(1);
        s.setUsername("Chen");
        StudentAssessmentDataDto first = new StudentAssessmentDataDto();
        first.setCloWeightedSums(new java.util.HashMap<>(Map.of(1, 6.0, 2, 4.0)));
        StudentAssessmentDataDto second = new StudentAssessmentDataDto();
        second.setCloWeightedSums(new java.util.HashMap<>(Map.of(1, 2.0)));
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("grandTotal", 72.0);
        data.put("evaluated", true);
        data.put("isOverridden", true);
        data.put("factor", 0.9);
        data.put("teamName", "Team A");
        data.put("assessmentDataList", List.of(first, second));

        StudentResult result = CohortInputs.toResults(List.of(s), Map.of(1L, data)).get(0);

        assertEquals("Chen", result.name());
        assertEquals(72.0, result.finalMark(), 0.001);
        assertTrue(result.overridden());
        assertTrue(result.evaluated());
        assertEquals(8.0, result.cloMarks().get(1), 0.001);
        assertEquals(4.0, result.cloMarks().get(2), 0.001);
    }
}
