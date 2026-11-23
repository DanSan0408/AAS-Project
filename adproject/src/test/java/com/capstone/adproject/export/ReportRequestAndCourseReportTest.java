package com.capstone.adproject.export;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.capstone.adproject.service.CohortMetrics;
import com.capstone.adproject.service.CohortMetrics.CriterionSample;
import com.capstone.adproject.service.CohortMetrics.StudentResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportRequestAndCourseReportTest {

    // --- what the browser sends --------------------------------------------------------------------------------------------

    private static ReportRequest.SectionRequest section(List<String> columns, List<List<Object>> rows) {
        return new ReportRequest.SectionRequest("Table", null, columns, rows);
    }

    private static List<Object> row(Object... cells) {
        return Arrays.asList(cells);
    }

    @Test
    void aWellFormedRequestBecomesADocumentWithTrimmedText() {
        ReportRequest request = new ReportRequest("  Marking consistency  ", " Final presentation ", List.of(" Team: all "),
                List.of(new ReportRequest.SectionRequest(" Assessors ", "  ", List.of("Lecturer", "Mean (%)"), List.of(row(" Dr Lim ", 71.5), row("Dr Tan", null)))));

        ReportDocument doc = request.toDocument();

        assertEquals("Marking consistency", doc.title());
        assertEquals("Final presentation", doc.subtitle());
        assertEquals(List.of("Team: all"), doc.details());
        assertEquals("Assessors", doc.sections().get(0).heading());
        assertNull(doc.sections().get(0).note(), "a blank note is dropped");
        assertEquals("Dr Lim", doc.sections().get(0).rows().get(0).get(0));
        assertEquals(71.5, doc.sections().get(0).rows().get(0).get(1));
    }

    @Test
    void aMissingTitleGetsADefaultAndExtraCellsBeyondTheColumnsAreDropped() {
        ReportRequest request = new ReportRequest(" ", null, null, List.of(section(List.of("a"), List.of(row("x", "extra", "more")))));

        ReportDocument doc = request.toDocument();

        assertEquals("Report", doc.title());
        assertEquals(1, doc.sections().get(0).rows().get(0).size());
    }

    @Test
    void anythingButTextNumbersAndBooleansInACellIsRefused() {
        Map<String, Object> sneaky = new HashMap<>();
        sneaky.put("a", 1);
        ReportRequest withMap = new ReportRequest("T", null, null, List.of(section(List.of("a"), List.of(row(sneaky)))));
        ReportRequest withList = new ReportRequest("T", null, null, List.of(section(List.of("a"), List.of(row(List.of(1, 2))))));

        assertThrows(IllegalArgumentException.class, withMap::toDocument);
        assertThrows(IllegalArgumentException.class, withList::toDocument);
    }

    @Test
    void notANumberBecomesAnEmptyCellAndOverlongTextIsCut() {
        ReportRequest request = new ReportRequest("T", null, null,
                List.of(section(List.of("a", "b"), List.of(row(Double.NaN, "x".repeat(5000))))));

        List<Object> cells = request.toDocument().sections().get(0).rows().get(0);

        assertNull(cells.get(0));
        assertEquals(ReportRequest.MAX_TEXT, ((String) cells.get(1)).length());
    }

    @Test
    void sizeLimitsStopAnOversizedRequestWithAClearMessage() {
        List<ReportRequest.SectionRequest> tooManyTables = new ArrayList<>();
        for (int i = 0; i <= ReportRequest.MAX_SECTIONS; i++) {
            tooManyTables.add(section(List.of("a"), List.of()));
        }
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new ReportRequest("T", null, null, tooManyTables).toDocument())
                .getMessage().contains("at most " + ReportRequest.MAX_SECTIONS));

        List<String> tooWide = new ArrayList<>();
        for (int i = 0; i <= ReportRequest.MAX_COLUMNS; i++) {
            tooWide.add("c" + i);
        }
        assertThrows(IllegalArgumentException.class, () -> new ReportRequest("T", null, null, List.of(section(tooWide, List.of()))).toDocument());

        List<List<Object>> tooMany = new ArrayList<>();
        for (int i = 0; i <= ReportRequest.MAX_ROWS; i++) {
            tooMany.add(row("x"));
        }
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new ReportRequest("T", null, null, List.of(section(List.of("a"), tooMany))).toDocument())
                .getMessage().contains("Narrow the filters"));
    }

    @Test
    void aRequestWithNoTablesIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new ReportRequest("T", null, null, List.of()).toDocument());
        assertThrows(IllegalArgumentException.class, () -> new ReportRequest("T", null, null, null).toDocument());
    }

    // --- the Course Analytics report ------------------------------------------------------------------------------------------

    private static StudentResult student(long id, String name, double mark, boolean evaluated, Map<Integer, Double> clo) {
        return new StudentResult(id, name, "Team " + (id % 2), mark, evaluated, false, 1.0, clo);
    }

    private static CourseAnalyticsReport.Input input(List<StudentResult> results, List<CriterionSample> samples) {
        return new CourseAnalyticsReport.Input("SECJ4999 Capstone", null, List.of("Final presentation", "Report"), 50, 60,
                CohortMetrics.summarise(results, 50, m -> m >= 70 ? "A" : (m >= 50 ? "C" : "F")),
                CohortMetrics.cloRows(results, Map.of(1, 20.0), 60), CohortMetrics.criterionRows(samples), CohortMetrics.attention(results, 50));
    }

    private static ReportDocument.Section find(ReportDocument doc, String heading) {
        return doc.sections().stream().filter(s -> s.heading().equals(heading)).findFirst()
                .orElseThrow(() -> new AssertionError("no table " + heading + " in " + doc.sections().stream().map(ReportDocument.Section::heading).toList()));
    }

    private static Object measure(ReportDocument.Section summary, String name) {
        return summary.rows().stream().filter(r -> r.get(0).equals(name)).findFirst().orElseThrow().get(1);
    }

    @Test
    void theReportCarriesTheSameFiguresAsThePage() {
        List<StudentResult> results = List.of(
                student(1, "Strong", 85, true, Map.of(1, 18.0)), student(2, "Near", 52, true, Map.of(1, 9.0)),
                student(3, "Fail", 30, true, Map.of(1, 4.0)), student(4, "Solid", 70, true, Map.of(1, 14.0)),
                student(5, "Absent", 0, false, Map.of()));
        List<CriterionSample> samples = java.util.stream.IntStream.range(0, 6)
                .mapToObj(i -> new CriterionSample("k", "Report - Clarity", 0.3, false, true)).toList();

        ReportDocument doc = CourseAnalyticsReport.build(input(results, samples));

        assertEquals("Course Analytics", doc.title());
        assertEquals("SECJ4999 Capstone", doc.subtitle());
        assertTrue(doc.details().contains("Team: All teams"));
        assertTrue(doc.details().contains("Assessments: Final presentation, Report"));
        assertTrue(doc.details().get(2).contains("learning outcome target 60%"));

        ReportDocument.Section summary = find(doc, "Summary");
        assertEquals(5, measure(summary, "Students in this selection"));
        assertEquals(4, measure(summary, "Students evaluated"));
        assertEquals(1, measure(summary, "Not evaluated yet (left out of the figures)"));
        assertEquals(59.3, measure(summary, "Average mark (out of 100)"));          // (85+52+30+70)/4 = 59.25
        assertEquals(75.0, measure(summary, "Pass rate (%)"));                       // 3 of 4 at or above 50
        assertEquals("Keep an eye on it", measure(summary, "Reading of the pass rate"));
        assertEquals(60.0, measure(summary, "Learning outcome target (%)"));

        ReportDocument.Section clo = find(doc, "Learning outcomes (CLO)");
        assertEquals("CLO 1", clo.rows().get(0).get(0));
        assertTrue(clo.rows().get(0).get(3) instanceof Boolean);

        ReportDocument.Section criteria = find(doc, "Criteria");
        assertEquals("Report - Clarity", criteria.rows().get(0).get(0));
        assertEquals("Hard", criteria.rows().get(0).get(5));

        ReportDocument.Section attention = find(doc, "Students who need a word");
        List<Object> absent = attention.rows().stream().filter(r -> r.get(0).equals("Absent")).findFirst().orElseThrow();
        assertEquals("Not evaluated yet", absent.get(3));
        assertNull(absent.get(2), "a student nobody has marked has no mark, not a zero");
    }

    @Test
    void theGradeShareIsOfTheEvaluatedStudentsOnly() {
        ReportDocument doc = CourseAnalyticsReport.build(input(List.of(
                student(1, "A1", 85, true, Map.of()), student(2, "A2", 90, true, Map.of()),
                student(3, "C1", 55, true, Map.of()), student(4, "None", 0, false, Map.of())), List.of()));

        ReportDocument.Section grades = find(doc, "Grade distribution");

        assertEquals(List.of("A", 2, 66.7), grades.rows().get(0));
        assertEquals(List.of("C", 1, 33.3), grades.rows().get(1));
    }

    @Test
    void whenNobodyIsEvaluatedThereIsNoInventedNumberAndNoGradeTable() {
        ReportDocument doc = CourseAnalyticsReport.build(input(List.of(student(1, "None", 0, false, Map.of())), List.of()));

        assertFalse(doc.sections().stream().anyMatch(s -> s.heading().equals("Grade distribution")));
        ReportDocument.Section summary = find(doc, "Summary");
        assertNull(measure(summary, "Average mark (out of 100)"));
        assertNull(measure(summary, "Pass rate (%)"));
        assertEquals("No data yet", measure(summary, "Reading of the pass rate"));
        assertEquals("Too few students to say", measure(summary, "How spread out the marks are"));
    }

    @Test
    void aChosenTeamIsNamedInTheDescription() {
        CourseAnalyticsReport.Input base = input(List.of(), List.of());
        CourseAnalyticsReport.Input team = new CourseAnalyticsReport.Input(base.courseLabel(), "Team Alpha", List.of(), base.passMark(), base.cloTarget(),
                base.summary(), base.cloRows(), base.criterionRows(), base.attention());

        ReportDocument doc = CourseAnalyticsReport.build(team);

        assertTrue(doc.details().contains("Team: Team Alpha"));
        assertTrue(doc.details().contains("Assessments: All assessments"));
    }
}
