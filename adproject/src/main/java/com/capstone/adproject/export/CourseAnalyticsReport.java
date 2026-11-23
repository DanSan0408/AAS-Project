package com.capstone.adproject.export;

import java.util.ArrayList;
import java.util.List;

import com.capstone.adproject.service.CohortMetrics;

/**
 * The Course Analytics page as a report. It is built from the same figures the page shows (see {@link CohortMetrics}), not
 * recalculated, so the file and the screen cannot disagree.
 */
public final class CourseAnalyticsReport {

    private CourseAnalyticsReport() {}

    public record Input(String courseLabel, String teamLabel, List<String> assessmentTitles, double passMark, double cloTarget,
                        CohortMetrics.Summary summary, List<CohortMetrics.CloRow> cloRows,
                        List<CohortMetrics.CriterionRow> criterionRows, List<CohortMetrics.AttentionRow> attention) {}

    public static ReportDocument build(Input in) {
        List<String> details = new ArrayList<>();
        details.add("Team: " + (in.teamLabel() == null || in.teamLabel().isBlank() ? "All teams" : in.teamLabel()));
        details.add("Assessments: " + (in.assessmentTitles() == null || in.assessmentTitles().isEmpty()
                ? "All assessments" : String.join(", ", in.assessmentTitles())));
        details.add("Pass mark " + whole(in.passMark()) + ", learning outcome target " + whole(in.cloTarget()) + "%. "
                + "Only submitted evaluations count; a student nobody has marked yet is not counted as a zero.");

        List<ReportDocument.Section> sections = new ArrayList<>();
        sections.add(summarySection(in));
        if (in.summary().marked() > 0) {
            sections.add(gradeSection(in.summary()));
        }
        sections.add(cloSection(in));
        sections.add(criteriaSection(in.criterionRows()));
        sections.add(attentionSection(in.attention()));
        return new ReportDocument("Course Analytics", in.courseLabel(), details, sections);
    }

    private static ReportDocument.Section summarySection(Input in) {
        CohortMetrics.Summary s = in.summary();
        List<List<Object>> rows = new ArrayList<>();
        rows.add(row("Students in this selection", s.students()));
        rows.add(row("Students evaluated", s.marked()));
        rows.add(row("Not evaluated yet (left out of the figures)", s.students() - s.marked()));
        rows.add(row("Average mark (out of 100)", number(s.average())));
        rows.add(row("Pass mark", round1(in.passMark())));
        rows.add(row("Pass rate (%)", number(s.passRate())));
        rows.add(row("Passed (students)", s.passCount()));
        rows.add(row("Reading of the pass rate", passReading(s.passLevel())));
        rows.add(row("Lowest mark", number(s.lowest())));
        rows.add(row("Median mark", number(s.median())));
        rows.add(row("Highest mark", number(s.highest())));
        rows.add(row("How spread out the marks are", spread(s)));
        rows.add(row("Learning outcome target (%)", round1(in.cloTarget())));
        return new ReportDocument.Section("Summary", null, List.of("Measure", "Value"), rows);
    }

    private static ReportDocument.Section gradeSection(CohortMetrics.Summary s) {
        List<List<Object>> rows = new ArrayList<>();
        for (CohortMetrics.GradeCount g : s.grades()) {
            rows.add(row(g.grade(), g.count(), round1(g.count() * 100.0 / s.marked())));
        }
        return new ReportDocument.Section("Grade distribution", "Share is of the evaluated students.",
                List.of("Grade", "Students", "Share (%)"), rows);
    }

    private static ReportDocument.Section cloSection(Input in) {
        List<List<Object>> rows = new ArrayList<>();
        for (CohortMetrics.CloRow c : in.cloRows()) {
            rows.add(row("CLO " + c.clo(), round1(c.attainmentPercent()), round1(c.target()), c.meetsTarget(), c.below(), c.measured()));
        }
        return new ReportDocument.Section("Learning outcomes (CLO)",
                "Attainment is the average share of the outcome's marks that evaluated students earned.",
                List.of("Outcome", "Attainment (%)", "Target (%)", "Meets target", "Students below target", "Students measured"), rows);
    }

    private static ReportDocument.Section criteriaSection(List<CohortMetrics.CriterionRow> criteria) {
        List<List<Object>> rows = new ArrayList<>();
        for (CohortMetrics.CriterionRow c : criteria) {
            rows.add(row(c.label(), round1(c.averagePercent()), round1(c.topShare()), round1(c.bottomShare()), c.n(), criterionReading(c.tag())));
        }
        return new ReportDocument.Section("Criteria",
                "Average is the share of the best rating. A criterion needs at least " + CohortMetrics.MIN_RATINGS_PER_CRITERION
                        + " ratings before it is called hard or easy.",
                List.of("Criterion", "Average (%)", "At the top level (%)", "At the bottom level (%)", "Ratings", "Reading"), rows);
    }

    private static ReportDocument.Section attentionSection(List<CohortMetrics.AttentionRow> attention) {
        List<List<Object>> rows = new ArrayList<>();
        for (CohortMetrics.AttentionRow a : attention) {
            rows.add(row(a.name(), a.team() == null ? "" : a.team(), number(a.finalMark()), String.join("; ", a.reasons())));
        }
        return new ReportDocument.Section("Students who need a word",
                "Below or close to the pass mark, with a low teamwork factor, an override, or not evaluated yet.",
                List.of("Student", "Team", "Final mark", "Why"), rows);
    }

    private static String passReading(String level) {
        return switch (level == null ? "" : level) {
            case "GOOD" -> "On track";
            case "WATCH" -> "Keep an eye on it";
            case "ACT" -> "Needs action";
            default -> "No data yet";
        };
    }

    private static String spread(CohortMetrics.Summary s) {
        if (!s.enoughForSpread() || s.spread() == null) {
            return "Too few students to say";
        }
        return switch (s.spread()) {
            case "CLOSE" -> "Close together";
            case "WIDE" -> "Wide";
            default -> "Mixed";
        };
    }

    private static String criterionReading(String tag) {
        return switch (tag == null ? "" : tag) {
            case "HARD" -> "Hard";
            case "EASY" -> "Easy";
            case "FEW" -> "Too few ratings to say";
            default -> "OK";
        };
    }

    private static List<Object> row(Object... cells) {
        List<Object> list = new ArrayList<>();
        for (Object cell : cells) {
            list.add(cell);
        }
        return list;
    }

    private static Object number(Double value) {
        return value == null ? null : round1(value);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static String whole(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(round1(value));
    }
}
