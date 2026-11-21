package com.capstone.adproject.service;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.capstone.adproject.service.CohortMetrics.AttentionRow;
import com.capstone.adproject.service.CohortMetrics.CloRow;
import com.capstone.adproject.service.CohortMetrics.CriterionRow;
import com.capstone.adproject.service.CohortMetrics.CriterionSample;
import com.capstone.adproject.service.CohortMetrics.StudentResult;
import com.capstone.adproject.service.CohortMetrics.Summary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The cohort numbers an educator reads: how the class is doing, which outcomes and criteria are weak, who needs a word. */
class CohortMetricsTest {

    private static StudentResult student(long id, String name, double mark, boolean evaluated) {
        return new StudentResult(id, name, "Team", mark, evaluated, false, 1.0, Map.of());
    }

    private static StudentResult withClo(long id, double mark, Map<Integer, Double> clo) {
        return new StudentResult(id, "S" + id, "Team", mark, true, false, 1.0, clo);
    }

    private static String grade(Double m) {
        return m >= 50 ? "C" : "F";
    }

    // --- how is the cohort doing ----------------------------------------------------------------------------------

    @Test
    void averageAndPassRateUseOnlyStudentsWhoHaveBeenEvaluated() {
        List<StudentResult> results = List.of(
                student(1, "A", 80, true), student(2, "B", 60, true), student(3, "C", 40, true), student(4, "D", 20, true),
                student(5, "Not marked", 0, false));

        Summary s = CohortMetrics.summarise(results, 50, CohortMetricsTest::grade);

        assertEquals(5, s.students());
        assertEquals(4, s.marked());
        assertEquals(50.0, s.average(), 0.001, "the unmarked student is not a zero");
        assertEquals(50.0, s.passRate(), 0.001);
        assertEquals(2, s.passCount());
    }

    @Test
    void passRateGetsATrafficLight() {
        assertEquals("GOOD", CohortMetrics.passLevel(85.0));
        assertEquals("WATCH", CohortMetrics.passLevel(70.0));
        assertEquals("ACT", CohortMetrics.passLevel(40.0));
        assertEquals("UNKNOWN", CohortMetrics.passLevel(null));
    }

    @Test
    void gradeDistributionCountsEachGradeInOrderOfFirstAppearanceByMarkDescending() {
        List<StudentResult> results = List.of(student(1, "A", 90, true), student(2, "B", 85, true), student(3, "C", 10, true));

        Summary s = CohortMetrics.summarise(results, 50, m -> m >= 80 ? "A" : (m >= 50 ? "C" : "F"));

        assertEquals(List.of("A", "F"), s.grades().stream().map(CohortMetrics.GradeCount::grade).toList());
        assertEquals(List.of(2, 1), s.grades().stream().map(CohortMetrics.GradeCount::count).toList());
    }

    @Test
    void spreadIsDescribedInWordsOnlyWithEnoughStudents() {
        List<StudentResult> close = List.of(student(1, "a", 70, true), student(2, "b", 72, true), student(3, "c", 74, true),
                student(4, "d", 75, true), student(5, "e", 76, true));
        List<StudentResult> wide = List.of(student(1, "a", 20, true), student(2, "b", 40, true), student(3, "c", 60, true),
                student(4, "d", 80, true), student(5, "e", 95, true));
        List<StudentResult> few = List.of(student(1, "a", 20, true), student(2, "b", 90, true));

        assertEquals("CLOSE", CohortMetrics.summarise(close, 50, CohortMetricsTest::grade).spread());
        assertEquals("WIDE", CohortMetrics.summarise(wide, 50, CohortMetricsTest::grade).spread());
        Summary tooFew = CohortMetrics.summarise(few, 50, CohortMetricsTest::grade);
        assertNull(tooFew.spread());
        assertFalse(tooFew.enoughForSpread());
        assertEquals(20.0, tooFew.lowest(), 0.001);
        assertEquals(90.0, tooFew.highest(), 0.001);
    }

    @Test
    void anEmptyOrUnmarkedClassHasNoAverageRatherThanZero() {
        Summary s = CohortMetrics.summarise(List.of(student(1, "a", 0, false)), 50, CohortMetricsTest::grade);

        assertNull(s.average());
        assertNull(s.passRate());
        assertEquals(0, s.marked());
    }

    // --- which learning outcomes are weak --------------------------------------------------------------------------

    @Test
    void cloAttainmentIsTheAverageShareOfAvailableMarksAndCountsStudentsBelowTarget() {
        List<StudentResult> results = List.of(
                withClo(1, 80, Map.of(1, 18.0, 2, 4.0)),
                withClo(2, 60, Map.of(1, 12.0, 2, 6.0)),
                withClo(3, 40, Map.of(1, 6.0, 2, 2.0)));

        List<CloRow> rows = CohortMetrics.cloRows(results, Map.of(1, 20.0, 2, 10.0), 50);

        CloRow clo1 = rows.get(0);
        assertEquals(1, clo1.clo());
        assertEquals((90.0 + 60.0 + 30.0) / 3, clo1.attainmentPercent(), 0.001);
        assertEquals(1, clo1.below());                 // only the 30% student
        CloRow clo2 = rows.get(1);
        assertEquals((40.0 + 60.0 + 20.0) / 3, clo2.attainmentPercent(), 0.001);
        assertEquals(2, clo2.below());                 // 40% and 20%
        assertEquals(3, clo2.measured());
    }

    @Test
    void cloAttainmentIgnoresStudentsNotEvaluatedAndOutcomesWithNoMarksAvailable() {
        List<StudentResult> results = List.of(withClo(1, 80, Map.of(1, 10.0)), student(2, "Not marked", 0, false));

        List<CloRow> rows = CohortMetrics.cloRows(results, Map.of(1, 20.0, 3, 0.0), 50);

        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0).measured());
        assertEquals(50.0, rows.get(0).attainmentPercent(), 0.001);
    }

    // --- which criteria are hard -----------------------------------------------------------------------------------

    private static List<CriterionSample> samples(String key, double fraction, boolean top, boolean bottom, int n) {
        return java.util.stream.IntStream.range(0, n).mapToObj(i -> new CriterionSample(key, "Criterion " + key, fraction, top, bottom)).toList();
    }

    @Test
    void aCriterionMostStudentsStruggleWithIsMarkedHard() {
        List<CriterionSample> all = new java.util.ArrayList<>();
        all.addAll(samples("A", 0.30, false, true, 8));
        all.addAll(samples("B", 0.95, true, false, 8));
        all.addAll(samples("C", 0.70, false, false, 8));

        List<CriterionRow> rows = CohortMetrics.criterionRows(all);

        assertEquals("HARD", rows.stream().filter(r -> r.label().equals("Criterion A")).findFirst().orElseThrow().tag());
        assertEquals("EASY", rows.stream().filter(r -> r.label().equals("Criterion B")).findFirst().orElseThrow().tag());
        assertEquals("OK", rows.stream().filter(r -> r.label().equals("Criterion C")).findFirst().orElseThrow().tag());
        assertEquals("Criterion A", rows.get(0).label(), "hardest first");
    }

    @Test
    void aCriterionWithTooFewRatingsIsNotJudged() {
        List<CriterionRow> rows = CohortMetrics.criterionRows(samples("A", 0.10, false, true, 3));

        assertEquals("FEW", rows.get(0).tag());
        assertEquals(3, rows.get(0).n());
    }

    @Test
    void topAndBottomSharesArePercentagesOfRatings() {
        List<CriterionSample> all = new java.util.ArrayList<>();
        all.addAll(samples("A", 1.0, true, false, 3));
        all.addAll(samples("A", 0.5, false, false, 5));
        all.addAll(samples("A", 0.2, false, true, 2));

        CriterionRow row = CohortMetrics.criterionRows(all).get(0);

        assertEquals(30.0, row.topShare(), 0.001);
        assertEquals(20.0, row.bottomShare(), 0.001);
        assertEquals((3 * 1.0 + 5 * 0.5 + 2 * 0.2) / 10 * 100, row.averagePercent(), 0.001);
    }

    // --- who needs attention -----------------------------------------------------------------------------------------

    @Test
    void studentsBelowOrNearThePassMarkAreListedWithAReason() {
        List<StudentResult> results = List.of(
                student(1, "Fail", 35, true), student(2, "Near", 52, true), student(3, "Safe", 80, true));

        List<AttentionRow> rows = CohortMetrics.attention(results, 50);

        assertEquals(List.of("Fail", "Near"), rows.stream().map(AttentionRow::name).toList());
        assertTrue(rows.get(0).reasons().get(0).contains("Below the pass mark"));
        assertTrue(rows.get(1).reasons().get(0).contains("close to the pass mark"));
    }

    @Test
    void lowTeamworkFactorOverrideAndNoEvaluationAreFlagged() {
        StudentResult lowFactor = new StudentResult(1, "Factor", "T", 80, true, false, 0.6, Map.of());
        StudentResult overridden = new StudentResult(2, "Override", "T", 80, true, true, 1.0, Map.of());
        StudentResult unmarked = student(3, "Unmarked", 0, false);

        List<AttentionRow> rows = CohortMetrics.attention(List.of(lowFactor, overridden, unmarked), 50);

        assertEquals(3, rows.size());
        assertTrue(rows.stream().filter(r -> r.name().equals("Factor")).findFirst().orElseThrow().reasons().get(0).contains("Teamwork"));
        assertTrue(rows.stream().filter(r -> r.name().equals("Override")).findFirst().orElseThrow().reasons().get(0).contains("override"));
        assertTrue(rows.stream().filter(r -> r.name().equals("Unmarked")).findFirst().orElseThrow().reasons().get(0).contains("Not evaluated"));
    }
}
