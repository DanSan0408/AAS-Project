package com.capstone.adproject.service;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.capstone.adproject.config.LegacyColumnCleanupMigrationRunner.SchemaFinding;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchemaCheckDetailTest {

    private static SchemaFinding missing(String table, String column) {
        return new SchemaFinding("MISSING_COLUMN", table, column, "x");
    }

    @Test
    void theAlertNamesTheMissingColumns() {
        assertEquals(": assessment_comments.ai_label, assessment_comments.bulk_decision",
                MonitoringService.namesOf(List.of(missing("assessment_comments", "ai_label"), missing("assessment_comments", "bulk_decision"))));
    }

    @Test
    void aLongListIsCutAndCounted() {
        List<SchemaFinding> many = java.util.stream.IntStream.range(0, 11).mapToObj(i -> missing("t", "c" + i)).toList();
        String text = MonitoringService.namesOf(many);
        assertEquals(true, text.endsWith("(+3 more)"), text);
    }

    @Test
    void fixedLegacyColumnsAreNotListedAsProblems() {
        assertEquals("", MonitoringService.namesOf(List.of(new SchemaFinding("LEGACY_RELAXED", "marks", "old", "made nullable"))));
    }
}
