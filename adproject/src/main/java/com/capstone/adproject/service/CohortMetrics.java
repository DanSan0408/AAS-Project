package com.capstone.adproject.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The few numbers an educator actually reads about a cohort: how the class is doing, which learning outcomes and criteria are
 * weak, and who needs a word. Pure arithmetic over plain inputs, so the rules are easy to read and to test.
 *
 * <p>Two rules run through everything: a student with no finished evaluation is not counted as a zero (they are listed as
 * "not evaluated yet"), and a pattern is not described in words when there are too few students or ratings to support it.
 */
public final class CohortMetrics {

    /** Fewer students than this and the spread is not described in words. */
    public static final int MIN_FOR_SPREAD = 5;
    /** Fewer ratings than this on a criterion and it is not called hard or easy. */
    public static final int MIN_RATINGS_PER_CRITERION = 5;
    /** A student this close above the pass mark is worth a conversation. */
    public static final double NEAR_PASS_MARGIN = 5.0;
    /** A teamwork factor below this means teammates rated the student clearly below the team average. */
    public static final double LOW_FACTOR = 0.8;

    private CohortMetrics() {}

    public record StudentResult(long id, String name, String team, double finalMark, boolean evaluated, boolean overridden,
                                Double factor, Map<Integer, Double> cloMarks) {}

    public record GradeCount(String grade, int count) {}

    public record Summary(int students, int marked, Double average, Double passRate, int passCount, double passMark,
                          String passLevel, List<GradeCount> grades, Double lowest, Double median, Double highest,
                          String spread, boolean enoughForSpread) {
        /** Bar width (0-100) for a grade's share of the evaluated students. */
        public int gradeWidth(int count) {
            return marked == 0 ? 0 : (int) Math.round(count * 100.0 / marked);
        }
    }

    public record CloRow(int clo, double attainmentPercent, int below, int measured, double target) {
        public int barWidth() {
            return (int) Math.max(0, Math.min(100, Math.round(attainmentPercent)));
        }

        public boolean meetsTarget() {
            return attainmentPercent >= target;
        }
    }

    public record CriterionSample(String key, String label, double fraction, boolean top, boolean bottom) {}

    public record CriterionRow(String label, double averagePercent, double topShare, double bottomShare, int n, String tag) {
        public int barWidth() {
            return (int) Math.max(0, Math.min(100, Math.round(averagePercent)));
        }
    }

    public record AttentionRow(String name, String team, Double finalMark, List<String> reasons) {}

    // --- how is the cohort doing ---------------------------------------------------------------------------------------

    public static Summary summarise(List<StudentResult> results, double passMark, Function<Double, String> gradeOf) {
        List<StudentResult> marked = results.stream().filter(StudentResult::evaluated).collect(Collectors.toList());
        if (marked.isEmpty()) {
            return new Summary(results.size(), 0, null, null, 0, passMark, passLevel(null), List.of(), null, null, null, null, false);
        }
        List<Double> marks = marked.stream().map(StudentResult::finalMark).sorted().collect(Collectors.toList());
        double average = marks.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        int passCount = (int) marks.stream().filter(m -> m >= passMark).count();
        double passRate = passCount * 100.0 / marked.size();

        Map<String, Integer> byGrade = new LinkedHashMap<>();
        marked.stream().sorted(Comparator.comparingDouble(StudentResult::finalMark).reversed())
                .forEach(r -> byGrade.merge(gradeOf.apply(r.finalMark()), 1, Integer::sum));
        List<GradeCount> grades = byGrade.entrySet().stream().map(e -> new GradeCount(e.getKey(), e.getValue())).collect(Collectors.toList());

        boolean enough = marked.size() >= MIN_FOR_SPREAD;
        String spread = null;
        if (enough) {
            double iqr = quantile(marks, 0.75) - quantile(marks, 0.25);
            spread = iqr <= 10 ? "CLOSE" : (iqr >= 25 ? "WIDE" : "MIXED");
        }
        return new Summary(results.size(), marked.size(), average, passRate, passCount, passMark, passLevel(passRate), grades,
                marks.get(0), quantile(marks, 0.5), marks.get(marks.size() - 1), spread, enough);
    }

    /** Traffic light for the pass rate: green from 80%, amber from 60%, red below. */
    public static String passLevel(Double passRate) {
        if (passRate == null) {
            return "UNKNOWN";
        }
        return passRate >= 80 ? "GOOD" : (passRate >= 60 ? "WATCH" : "ACT");
    }

    /** Type-7 (linear interpolation) quantile of an ascending list. */
    static double quantile(List<Double> ascending, double q) {
        if (ascending.size() == 1) {
            return ascending.get(0);
        }
        double position = (ascending.size() - 1) * q;
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        return ascending.get(lower) + (position - lower) * (ascending.get(upper) - ascending.get(lower));
    }

    // --- which learning outcomes are weak ------------------------------------------------------------------------------

    /**
     * For each learning outcome (CLO): the average share of the available marks that evaluated students earned, and how many
     * fell below the target. {@code available} is the marks on offer per CLO across the assessments in view.
     */
    public static List<CloRow> cloRows(List<StudentResult> results, Map<Integer, Double> available, double targetPercent) {
        List<StudentResult> marked = results.stream().filter(StudentResult::evaluated).collect(Collectors.toList());
        List<CloRow> rows = new ArrayList<>();
        available.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            double offered = entry.getValue() == null ? 0.0 : entry.getValue();
            if (offered <= 0 || marked.isEmpty()) {
                return;
            }
            double total = 0;
            int below = 0;
            for (StudentResult student : marked) {
                double percent = student.cloMarks().getOrDefault(entry.getKey(), 0.0) / offered * 100.0;
                total += percent;
                if (percent < targetPercent) {
                    below++;
                }
            }
            rows.add(new CloRow(entry.getKey(), total / marked.size(), below, marked.size(), targetPercent));
        });
        return rows;
    }

    // --- which criteria are hard ----------------------------------------------------------------------------------------

    /** Per criterion: average score as a share of the best rating, and how many ratings sit at the top and at the bottom level. */
    public static List<CriterionRow> criterionRows(List<CriterionSample> samples) {
        Map<String, List<CriterionSample>> byKey = new LinkedHashMap<>();
        for (CriterionSample sample : samples) {
            byKey.computeIfAbsent(sample.key(), k -> new ArrayList<>()).add(sample);
        }
        List<CriterionRow> rows = new ArrayList<>();
        for (List<CriterionSample> group : byKey.values()) {
            int n = group.size();
            double average = group.stream().mapToDouble(CriterionSample::fraction).average().orElse(0.0) * 100.0;
            double top = group.stream().filter(CriterionSample::top).count() * 100.0 / n;
            double bottom = group.stream().filter(CriterionSample::bottom).count() * 100.0 / n;
            String tag;
            if (n < MIN_RATINGS_PER_CRITERION) {
                tag = "FEW";
            } else if (average < 50 || bottom > 50) {
                tag = "HARD";
            } else if (top > 80) {
                tag = "EASY";
            } else {
                tag = "OK";
            }
            rows.add(new CriterionRow(group.get(0).label(), average, top, bottom, n, tag));
        }
        rows.sort(Comparator.comparing((CriterionRow r) -> "FEW".equals(r.tag()) ? 1 : 0).thenComparingDouble(CriterionRow::averagePercent));
        return rows;
    }

    // --- who needs attention -----------------------------------------------------------------------------------------------

    public static List<AttentionRow> attention(List<StudentResult> results, double passMark) {
        List<AttentionRow> rows = new ArrayList<>();
        for (StudentResult s : results) {
            List<String> reasons = new ArrayList<>();
            if (!s.evaluated()) {
                reasons.add("Not evaluated yet");
            } else {
                if (s.finalMark() < passMark) {
                    reasons.add(String.format("Below the pass mark (%.1f, pass is %.0f)", s.finalMark(), passMark));
                } else if (s.finalMark() < passMark + NEAR_PASS_MARGIN) {
                    reasons.add(String.format("Only %.1f marks above the pass mark: close to the pass mark", s.finalMark() - passMark));
                }
                if (s.factor() != null && s.factor() > 0 && s.factor() < LOW_FACTOR) {
                    reasons.add(String.format("Teamwork factor is low (%.2f): teammates rated them below the team average", s.factor()));
                }
                if (s.overridden()) {
                    reasons.add("Result was set by an admin override");
                }
            }
            if (!reasons.isEmpty()) {
                rows.add(new AttentionRow(s.name(), s.team(), s.evaluated() ? s.finalMark() : null, reasons));
            }
        }
        rows.sort(Comparator.comparing((AttentionRow r) -> r.finalMark() == null ? 1 : 0)
                .thenComparing(r -> r.finalMark() == null ? 0.0 : r.finalMark()));
        return rows;
    }
}
