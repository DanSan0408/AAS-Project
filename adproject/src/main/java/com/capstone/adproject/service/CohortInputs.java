package com.capstone.adproject.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.capstone.adproject.dto.StudentAssessmentDataDto;
import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Mark;
import com.capstone.adproject.model.Rating;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Student;
import com.capstone.adproject.model.SubRubric;
import com.capstone.adproject.service.CohortMetrics.CriterionSample;
import com.capstone.adproject.service.CohortMetrics.StudentResult;

/** Turns what the data views already compute (results, rubrics, finished marks) into the plain inputs of {@link CohortMetrics}. */
public final class CohortInputs {

    private CohortInputs() {}

    /** The marks on offer per learning outcome (CLO) across the assessments in view. */
    public static Map<Integer, Double> cloAvailable(List<Assessment> assessments) {
        Map<Integer, Double> available = new LinkedHashMap<>();
        for (Assessment assessment : assessments) {
            if (assessment.getRubrics() == null) {
                continue;
            }
            for (Rubric rubric : assessment.getRubrics()) {
                if (rubric.getClo() != null && rubric.getMarks() != null) {
                    available.merge(rubric.getClo(), rubric.getMarks().doubleValue(), Double::sum);
                }
            }
        }
        return available;
    }

    /** One row per student from the overall-data map the data views build (final mark, evaluated flag, factor, CLO marks). */
    @SuppressWarnings("unchecked")
    public static List<StudentResult> toResults(List<Student> students, Map<Long, Map<String, Object>> overall) {
        List<StudentResult> results = new ArrayList<>();
        for (Student student : students) {
            Map<String, Object> data = overall.get(student.getId());
            if (data == null) {
                continue;
            }
            Map<Integer, Double> cloMarks = new HashMap<>();
            Object details = data.get("assessmentDataList");
            if (details instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof StudentAssessmentDataDto dto && dto.getCloWeightedSums() != null) {
                        dto.getCloWeightedSums().forEach((clo, value) -> cloMarks.merge(clo, value == null ? 0.0 : value, Double::sum));
                    }
                }
            }
            String name = student.getUsername() != null && !student.getUsername().isBlank()
                    ? student.getUsername().trim() : (student.getEmail() != null ? student.getEmail() : "Student #" + student.getId());
            Double grandTotal = (Double) data.get("grandTotal");
            results.add(new StudentResult(
                    student.getId() != null ? student.getId() : 0L, name,
                    String.valueOf(data.getOrDefault("teamName", "No Team")),
                    grandTotal != null ? grandTotal : 0.0,
                    Boolean.TRUE.equals(data.get("evaluated")),
                    Boolean.TRUE.equals(data.get("isOverridden")),
                    (Double) data.get("factor"),
                    cloMarks));
        }
        return results;
    }

    /**
     * One sample per rating given on a criterion (a sub-rubric, or a rubric with direct ratings). Where assessors marked a rubric
     * their ratings are used; student ratings only when there are none. A group rubric is stored once per team member, so it is
     * counted once per assessor and team, not once per member.
     *
     * @param marksByStudent per assessment, the finished marks received by each student id
     * @param groupIdByStudent the team (group id) of each student id, where there is one
     */
    public static List<CriterionSample> criterionSamples(List<Assessment> assessments,
                                                         Map<Long, Map<Long, List<Mark>>> marksByStudent,
                                                         Map<Long, Long> groupIdByStudent) {
        List<CriterionSample> samples = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Assessment assessment : assessments) {
            if (assessment.getRubrics() == null) {
                continue;
            }
            Map<Long, List<Mark>> byStudent = marksByStudent.getOrDefault(assessment.getId(), Map.of());
            for (Rubric rubric : assessment.getRubrics()) {
                boolean group = "Group Assessment".equalsIgnoreCase(rubric.getAssessmentTypes() == null ? "" : rubric.getAssessmentTypes().trim());
                for (Map.Entry<Long, List<Mark>> entry : byStudent.entrySet()) {
                    List<Mark> rubricMarks = entry.getValue().stream()
                            .filter(m -> m.getRubric() != null && rubric.getId() != null && rubric.getId().equals(m.getRubric().getId()))
                            .collect(Collectors.toList());
                    List<Mark> assessorMarks = rubricMarks.stream().filter(m -> m.getLecturer() != null).collect(Collectors.toList());
                    if (!assessorMarks.isEmpty()) {
                        rubricMarks = assessorMarks;
                    }
                    Long groupId = groupIdByStudent.get(entry.getKey());
                    String unit = group && groupId != null ? "g" + groupId : "s" + entry.getKey();
                    for (Mark mark : rubricMarks) {
                        addSample(samples, seen, assessment, rubric, mark, unit);
                    }
                }
            }
        }
        return samples;
    }

    private static void addSample(List<CriterionSample> samples, Set<String> seen, Assessment assessment, Rubric rubric, Mark mark, String unit) {
        if (mark.getRating() == null || mark.getRating().getMarks() == null) {
            return;
        }
        SubRubric sub = mark.getSubRubric();
        List<Rating> levels = sub != null ? sub.getRatings() : rubric.getRatings();
        if (levels == null || levels.isEmpty()) {
            return;
        }
        double max = levels.stream().filter(r -> r.getMarks() != null).mapToDouble(r -> r.getMarks().doubleValue()).max().orElse(0.0);
        double min = levels.stream().filter(r -> r.getMarks() != null).mapToDouble(r -> r.getMarks().doubleValue()).min().orElse(0.0);
        if (max <= 0) {
            return;
        }
        String criterionKey = assessment.getId() + ":" + (sub != null ? "s" + sub.getId() : "r" + rubric.getId());
        String evaluator = mark.getLecturer() != null ? "L" + mark.getLecturer().getId()
                : (mark.getEvaluatorStudent() != null ? "S" + mark.getEvaluatorStudent().getId() : "?");
        if (!seen.add(evaluator + "|" + unit + "|" + criterionKey)) {
            return;
        }
        double value = mark.getRating().getMarks().doubleValue();
        String label = (rubric.getName() != null ? rubric.getName() : "Rubric")
                + (sub != null && sub.getName() != null ? " - " + sub.getName() : "");
        samples.add(new CriterionSample(criterionKey, label, value / max, value >= max, max > min && value <= min));
    }
}
