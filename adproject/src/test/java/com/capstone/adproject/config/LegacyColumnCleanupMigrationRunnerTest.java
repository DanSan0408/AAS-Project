package com.capstone.adproject.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LegacyColumnCleanupMigrationRunnerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private LegacyColumnCleanupMigrationRunner runner;

    @BeforeEach
    void setUp() {
    }

    @Test
    void testRun_WhenLegacyColumnExistsAndCurrentColumnExists_CopiesAndDropsLegacy() {
        // Mock query for 'marks' table having legacy 'submittedAt'
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("marks"), eq("submittedAt")))
                .thenReturn(1);
        // Mock query for 'marks' table having current 'submitted_at'
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("marks"), eq("submitted_at")))
                .thenReturn(1);

        // Mock query for 'assessment_comments' table not having legacy column
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("assessment_comments"), eq("submittedAt")))
                .thenReturn(0);

        assertDoesNotThrow(() -> runner.run());

        verify(jdbcTemplate).execute("UPDATE `marks` SET `submitted_at` = `submittedAt` WHERE `submitted_at` IS NULL AND `submittedAt` IS NOT NULL");
        verify(jdbcTemplate).execute("ALTER TABLE `marks` DROP COLUMN `submittedAt`");
    }

    @Test
    void testRun_WhenDropFails_FallsBackToModifyNullable() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("marks"), eq("submittedAt")))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("marks"), eq("submitted_at")))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("assessment_comments"), eq("submittedAt")))
                .thenReturn(0);

        // Simulate DROP COLUMN failure (e.g., MySQL version lock)
        org.mockito.Mockito.doThrow(new DataAccessException("Cannot drop column") {})
                .when(jdbcTemplate).execute("ALTER TABLE `marks` DROP COLUMN `submittedAt`");

        assertDoesNotThrow(() -> runner.run());

        verify(jdbcTemplate).execute("ALTER TABLE `marks` MODIFY COLUMN `submittedAt` DATETIME NULL DEFAULT NULL");
    }

    @Test
    void testRun_WhenDatabaseThrowsException_HandlesGracefullyWithoutThrowing() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("marks"), eq("submittedAt")))
                .thenThrow(new DataAccessException("Connection failed") {});

        assertDoesNotThrow(() -> runner.run());
    }

    @Test
    void testRun_WhenCoursesHasLegacyCourseCodeAndName_CopiesAndDropsLegacy() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("courses"), eq("course_code")))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("courses"), eq("courseCode")))
                .thenReturn(1);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("courses"), eq("course_name")))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("courses"), eq("courseName")))
                .thenReturn(1);

        assertDoesNotThrow(() -> runner.run());

        verify(jdbcTemplate).execute("UPDATE `courses` SET `courseCode` = `course_code` WHERE `courseCode` IS NULL AND `course_code` IS NOT NULL");
        verify(jdbcTemplate).execute("ALTER TABLE `courses` DROP COLUMN `course_code`");
        verify(jdbcTemplate).execute("UPDATE `courses` SET `courseName` = `course_name` WHERE `courseName` IS NULL AND `course_name` IS NOT NULL");
        verify(jdbcTemplate).execute("ALTER TABLE `courses` DROP COLUMN `course_name`");
    }

    @Test
    void testRun_WhenCourseCodeDropFails_FallsBackToModifyNullableVarchar() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("courses"), eq("course_code")))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("courses"), eq("courseCode")))
                .thenReturn(1);

        org.mockito.Mockito.doThrow(new DataAccessException("Cannot drop column") {})
                .when(jdbcTemplate).execute("ALTER TABLE `courses` DROP COLUMN `course_code`");

        assertDoesNotThrow(() -> runner.run());

        verify(jdbcTemplate).execute("ALTER TABLE `courses` MODIFY COLUMN `course_code` VARCHAR(255) NULL DEFAULT NULL");
    }

    // ---- generic schema guard ----

    private static LegacyColumnCleanupMigrationRunner.DbColumn column(String table, String name, boolean nullable,
            boolean hasDefault, boolean primaryKey) {
        return new LegacyColumnCleanupMigrationRunner.DbColumn(table, name, "varchar(255)", nullable, hasDefault,
            primaryKey, false, null, null);
    }

    @Test
    void analyse_FlagsAnUnusedRequiredColumnWithNoDefault_TheCauseOfInsertFailures() {
        var mapped = java.util.Map.of("marks", java.util.Set.of("id", "score"));
        var db = java.util.List.of(
            column("marks", "id", false, false, true),
            column("marks", "score", true, false, false),
            column("marks", "old_weight", false, false, false));

        var findings = LegacyColumnCleanupMigrationRunner.analyse(mapped, db);

        org.junit.jupiter.api.Assertions.assertEquals(1, findings.size());
        org.junit.jupiter.api.Assertions.assertEquals("LEGACY_BLOCKING", findings.get(0).kind());
        org.junit.jupiter.api.Assertions.assertEquals("old_weight", findings.get(0).column());
    }

    @Test
    void analyse_IgnoresUnusedColumnsThatAreOptionalOrHaveADefault() {
        var mapped = java.util.Map.of("marks", java.util.Set.of("id"));
        var db = java.util.List.of(
            column("marks", "id", false, false, true),
            column("marks", "optional_old", true, false, false),
            column("marks", "defaulted_old", false, true, false));

        org.junit.jupiter.api.Assertions.assertTrue(LegacyColumnCleanupMigrationRunner.analyse(mapped, db).isEmpty());
    }

    @Test
    void analyse_NeverTouchesTablesTheCodeDoesNotMap() {
        var mapped = java.util.Map.of("marks", java.util.Set.of("id"));
        var db = java.util.List.of(
            column("marks", "id", false, false, true),
            column("somebody_elses_table", "required_col", false, false, false));

        org.junit.jupiter.api.Assertions.assertTrue(LegacyColumnCleanupMigrationRunner.analyse(mapped, db).isEmpty());
    }

    @Test
    void analyse_ReportsAColumnTheCodeNeedsButTheDatabaseLacks() {
        var mapped = java.util.Map.of("marks", java.util.Set.of("id", "clo1"));
        var db = java.util.List.of(column("marks", "id", false, false, true));

        var findings = LegacyColumnCleanupMigrationRunner.analyse(mapped, db);

        org.junit.jupiter.api.Assertions.assertEquals(1, findings.size());
        org.junit.jupiter.api.Assertions.assertEquals("MISSING_COLUMN", findings.get(0).kind());
        org.junit.jupiter.api.Assertions.assertEquals("clo1", findings.get(0).column());
    }

    @Test
    void analyse_MatchesNamesCaseInsensitively() {
        var mapped = java.util.Map.of("marks", java.util.Set.of("submittedat"));
        var db = java.util.List.of(column("Marks", "submittedAt", true, false, false));

        org.junit.jupiter.api.Assertions.assertTrue(LegacyColumnCleanupMigrationRunner.analyse(mapped, db).isEmpty());
    }

    @Test
    void guardSchema_WithoutEntityMetadata_IsASafeNoOp() {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> runner.guardSchema());
        org.junit.jupiter.api.Assertions.assertTrue(runner.getFindings().isEmpty());
    }
}
