package com.capstone.adproject.service;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.dto.analytics.LecturerComparisonData;
import com.capstone.adproject.dto.analytics.LecturerComparisonData.Criterion;
import com.capstone.adproject.dto.analytics.LecturerComparisonData.Option;
import com.capstone.adproject.dto.analytics.LecturerComparisonData.Score;
import com.capstone.adproject.model.Assessment;
import com.capstone.adproject.model.Mark;
import com.capstone.adproject.model.Rubric;
import com.capstone.adproject.model.Rating;
import com.capstone.adproject.repositories.AssessmentRepository;
import com.capstone.adproject.repositories.GroupRepository;
import com.capstone.adproject.repositories.LecturerAnalyticsMark;
import com.capstone.adproject.repositories.LecturerRepository;
import com.capstone.adproject.repositories.MarkRepository;

@Service
@Transactional(readOnly = true)
public class LecturerAnalyticsService {
    private final AssessmentRepository assessments;
    private final MarkRepository marks;
    private final GroupRepository groups;
    private final LecturerRepository lecturers;

    public LecturerAnalyticsService(AssessmentRepository assessments, MarkRepository marks,
                                    GroupRepository groups, LecturerRepository lecturers) {
        this.assessments = assessments;
        this.marks = marks;
        this.groups = groups;
        this.lecturers = lecturers;
    }

    public List<Option> assessmentsForCourse(Long courseId) {
        if (courseId == null) return List.of();
        return assessments.findAllWithRubricsByCourseId(courseId).stream()
            .sorted(Comparator.comparing(a -> label(a.getTitle(), "Assessment " + a.getId()), String.CASE_INSENSITIVE_ORDER))
            .map(a -> new Option(a.getId().toString(), label(a.getTitle(), "Assessment " + a.getId())))
            .toList();
    }

    public LecturerComparisonData comparison(Long courseId, Long assessmentId) {
        if (courseId == null || assessmentId == null) throw new IllegalArgumentException("Select an assessment in the active course.");
        Assessment assessment = assessments.findById(assessmentId)
            .filter(a -> a.getCourse() != null && Objects.equals(courseId, a.getCourse().getId()))
            .orElseThrow(() -> new IllegalArgumentException("Assessment is not available in the active course."));
        Map<String, Criterion> criteria = criteria(assessment);
        List<Option> teams = new ArrayList<>(groups.findByCourseId(courseId).stream()
            .sorted(Comparator.comparing(g -> label(g.getGroupName(), "Team " + g.getId()), String.CASE_INSENSITIVE_ORDER))
            .map(g -> new Option(g.getId().toString(), label(g.getGroupName(), "Team " + g.getId())))
            .toList());
        Map<Long, String> teamNames = new HashMap<>();
        teams.forEach(t -> teamNames.put(Long.valueOf(t.id()), t.label()));
        List<LecturerAnalyticsMark> rows = marks.findLecturerAnalyticsMarks(assessmentId, courseId,
            List.of(Mark.SubmissionStatus.FINAL, Mark.SubmissionStatus.SUBMITTED));
        Map<Long, String> legacyNames = new HashMap<>();
        List<Long> legacyIds = rows.stream().filter(r -> r.getLecturerId() == null)
            .map(r -> legacyLecturerId(r.getSourceTag())).filter(Objects::nonNull).distinct().toList();
        if (!legacyIds.isEmpty()) lecturers.findAllById(legacyIds).forEach(l ->
            legacyNames.put(l.getId(), label(l.getUsername(), "Lecturer " + l.getId())));

        // Group evaluations are copied to each team member by the evaluation service.
        // One latest row per lecturer + target + criterion prevents weighting by team size.
        Map<String, LecturerAnalyticsMark> latest = new LinkedHashMap<>();
        int excluded = 0;
        for (LecturerAnalyticsMark row : rows) {
            Long lecturerId = lecturerId(row);
            String key = criterionId(row);
            Criterion criterion = criteria.get(key);
            if (lecturerId == null || row.getStudentId() == null || criterion == null
                    || !isCommitted(row.getStatus()) || !Objects.equals(criterion.scope(), scope(row.getAssessmentType()))
                    || row.getEarned() == null || !Double.isFinite(row.getEarned())
                    || row.getEarned() < 0 || row.getEarned() > criterion.possible()
                    || (row.getTeamId() != null && !teamNames.containsKey(row.getTeamId()))) {
                excluded++;
                continue;
            }
            String identity = lecturerId + ":" + targetId(row, criterion) + ":" + key;
            latest.merge(identity, row, (a, b) -> newest(a, b));
        }
        List<Score> scores = new ArrayList<>();
        for (LecturerAnalyticsMark row : latest.values()) {
            Criterion criterion = criteria.get(criterionId(row));
            Long id = lecturerId(row);
            String name = label(row.getLecturerName(), legacyNames.getOrDefault(id, "Lecturer " + id));
            String studentName = label(row.getStudentName(), "Student " + row.getStudentId());
            String teamName = row.getTeamId() == null ? "Unassigned team" : teamNames.get(row.getTeamId());
            boolean group = "GROUP".equals(criterion.scope()) && row.getTeamId() != null;
            scores.add(new Score(id, name, row.getTeamId(), teamName,
                group ? null : row.getStudentId(), group ? null : studentName,
                targetId(row, criterion), group ? teamName : studentName,
                criterion.id(), row.getEarned(), row.getStatus().name(),
                row.getSubmittedAt() == null ? null : row.getSubmittedAt().toString()));
        }
        scores.sort(Comparator.comparing(Score::lecturerName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Score::targetId).thenComparing(Score::criterionId));
        if (scores.stream().anyMatch(s -> s.teamId() == null)) teams.add(new Option("unassigned", "Unassigned team"));
        return new LecturerComparisonData(assessmentId, label(assessment.getTitle(), "Assessment " + assessmentId),
            List.copyOf(teams), List.copyOf(criteria.values()), List.copyOf(scores), excluded);
    }

    private Map<String, Criterion> criteria(Assessment assessment) {
        Map<String, Criterion> result = new LinkedHashMap<>();
        for (Rubric rubric : assessment.getRubrics()) {
            String scope = scope(rubric.getAssessmentTypes());
            if (scope == null) continue; // Peer and self rubrics never enter lecturer comparisons.
            String name = label(rubric.getName(), "Rubric " + rubric.getId());
            if (rubric.getSubRubrics().isEmpty()) {
                double max = maximum(rubric.getRatings(), rubric.getMarks());
                if (Double.isFinite(max) && max > 0) result.put("r" + rubric.getId(),
                    new Criterion("r" + rubric.getId(), rubric.getId(), name, name, rubric.getClo(), scope, max));
            } else {
                rubric.getSubRubrics().forEach(sub -> {
                    double max = maximum(sub.getRatings(), sub.getMarks());
                    if (Double.isFinite(max) && max > 0) result.put("s" + sub.getId(),
                        new Criterion("s" + sub.getId(), rubric.getId(), name,
                            label(sub.getName(), "Criterion " + sub.getId()), rubric.getClo(), scope, max));
                });
            }
        }
        return result;
    }

    // A rubric's total marks may be a weight (e.g. 20), while ratings use a 1–5
    // scale. Compare achieved ratings against that scale, not the rubric weight.
    private static double maximum(List<Rating> ratings, BigDecimal legacyMaximum) {
        if (ratings == null || ratings.isEmpty()) return legacyMaximum == null ? 0 : legacyMaximum.doubleValue();
        return ratings.stream().map(Rating::getMarks).filter(Objects::nonNull)
            .mapToDouble(BigDecimal::doubleValue).filter(Double::isFinite).max().orElse(0);
    }

    private static boolean isCommitted(Mark.SubmissionStatus status) {
        return status == Mark.SubmissionStatus.FINAL || status == Mark.SubmissionStatus.SUBMITTED;
    }
    private static String criterionId(LecturerAnalyticsMark row) {
        return row.getSubRubricId() != null ? "s" + row.getSubRubricId() : "r" + row.getRubricId();
    }
    private static String targetId(LecturerAnalyticsMark row, Criterion criterion) {
        return "GROUP".equals(criterion.scope()) && row.getTeamId() != null
            ? "GROUP:" + row.getTeamId() : criterion.scope() + ":STUDENT:" + row.getStudentId();
    }
    private static Long lecturerId(LecturerAnalyticsMark row) {
        return row.getLecturerId() != null ? row.getLecturerId() : legacyLecturerId(row.getSourceTag());
    }
    private static Long legacyLecturerId(String tag) {
        if (tag == null || !tag.matches("LECTURER:[0-9]+")) return null;
        try { return Long.valueOf(tag.substring(9)); } catch (NumberFormatException e) { return null; }
    }
    private static LecturerAnalyticsMark newest(LecturerAnalyticsMark a, LecturerAnalyticsMark b) {
        int order = Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder()).compare(a.getSubmittedAt(), b.getSubmittedAt());
        if (order == 0) order = Comparator.nullsFirst(Comparator.<Long>naturalOrder()).compare(a.getId(), b.getId());
        return order >= 0 ? a : b;
    }
    private static String scope(String type) {
        if (type == null) return null;
        return switch (type.trim().toLowerCase(Locale.ROOT)) {
            case "group assessment", "group" -> "GROUP";
            case "individual assessment", "individual" -> "INDIVIDUAL";
            default -> null;
        };
    }
    private static String label(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
