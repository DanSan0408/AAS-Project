package com.capstone.adproject.dto.analytics;

import java.util.List;

/** Immutable analytics snapshot; no persistence entities or private lecturer fields leave the service. */
public record LecturerComparisonData(
        Long assessmentId, String assessmentTitle, List<Option> teams, List<Criterion> criteria,
        List<Score> scores, int excludedRows) {
    public record Option(String id, String label) {}
    public record Criterion(String id, Long rubricId, String rubricName, String label,
                            Integer clo, String scope, double possible) {}
    public record Score(Long lecturerId, String lecturerName, Long teamId, String teamName,
                        Long studentId, String studentName, String targetId, String targetName,
                        String criterionId, double earned, String status, String submittedAt) {}
}
