package com.capstone.adproject.service;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import com.capstone.adproject.config.LegacyColumnCleanupMigrationRunner;
import com.capstone.adproject.config.LegacyColumnCleanupMigrationRunner.SchemaFinding;
import com.capstone.adproject.service.MonitoringService.Check;
import com.capstone.adproject.service.MonitoringService.Level;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonitoringServiceTest {

    @TempDir
    Path uploads;

    private JdbcTemplate jdbc;
    private AiServiceClient ai;
    private EmailService email;
    private LegacyColumnCleanupMigrationRunner schemaGuard;
    private MonitoringService monitoring;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        ai = mock(AiServiceClient.class);
        email = mock(EmailService.class);
        schemaGuard = mock(LegacyColumnCleanupMigrationRunner.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(ai.isServiceAvailable()).thenReturn(true);
        when(email.effectiveFromAddress()).thenReturn("no-reply@example.com");
        when(schemaGuard.getFindings()).thenReturn(List.of());
        monitoring = new MonitoringService(new SimpleMeterRegistry(), jdbc, ai, email, schemaGuard, uploads.toString(), "abc1234");
    }

    private Check check(MonitoringService.Snapshot snapshot, String name) {
        return snapshot.checks().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void everythingHealthyReportsUp() {
        var snapshot = monitoring.snapshot();

        assertEquals("abc1234", snapshot.revision());
        assertEquals(Level.UP, snapshot.overall());
        assertEquals(Level.UP, check(snapshot, "Database").level());
        assertEquals(Level.UP, check(snapshot, "Upload storage").level());
    }

    @Test
    void aDownDatabaseMakesTheWholeSystemDownButOtherProbesStillReport() {
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenThrow(new RuntimeException("Communications link failure"));

        var snapshot = monitoring.snapshot();

        assertEquals(Level.DOWN, snapshot.overall());
        assertEquals(Level.DOWN, check(snapshot, "Database").level());
        assertEquals(Level.UP, check(snapshot, "AI service").level(), "one failing probe must not hide the others");
    }

    @Test
    void anUnavailableAiServiceOnlyDegradesTheSystem() {
        when(ai.isServiceAvailable()).thenReturn(false);

        var snapshot = monitoring.snapshot();

        assertEquals(Level.WARN, snapshot.overall());
        assertEquals(Level.WARN, check(snapshot, "AI service").level());
    }

    @Test
    void aMailMisconfigurationIsSurfacedWithItsExplanation() {
        when(email.mailConfigProblem()).thenReturn("From address is the Brevo SMTP login");

        Check mail = check(monitoring.snapshot(), "Email");

        assertEquals(Level.WARN, mail.level());
        assertTrue(mail.detail().contains("Brevo"));
    }

    @Test
    void aBlockingLegacyColumnOrMissingColumnMarksTheSchemaDown() {
        when(schemaGuard.getFindings()).thenReturn(List.of(new SchemaFinding("MISSING_COLUMN", "marks", "clo1", "missing")));

        assertEquals(Level.DOWN, check(monitoring.snapshot(), "Database schema").level());
    }

    @Test
    void anAutomaticallyFixedLegacyColumnKeepsTheSchemaHealthyButIsVisible() {
        when(schemaGuard.getFindings()).thenReturn(List.of(new SchemaFinding("LEGACY_RELAXED", "marks", "old", "fixed")));

        Check schema = check(monitoring.snapshot(), "Database schema");

        assertEquals(Level.UP, schema.level());
        assertTrue(schema.detail().contains("1 legacy column"));
    }

    @Test
    void aRuntimeSchemaFaultDegradesTheSchemaCheckUntilRestart() {
        monitoring.recordSchemaIncident("Unknown column 'x' in 'field list'");

        Check schema = check(monitoring.snapshot(), "Database schema");

        assertEquals(Level.WARN, schema.level());
        assertTrue(schema.detail().contains("Unknown column"));
    }

    @Test
    void onlyServerErrorsAreKeptAndTheBufferIsBounded() {
        monitoring.recordError("A1", 404, "GET", "/nope", new RuntimeException("x"));
        for (int i = 0; i < 120; i++) {
            monitoring.recordError("E" + i, 500, "GET", "/boom", new IllegalStateException("fail " + i));
        }

        var recent = monitoring.getRecentErrors();

        assertEquals(100, recent.size());
        assertEquals("E119", recent.get(0).errorId(), "newest first");
        var snapshot = monitoring.snapshot();
        assertEquals(1, snapshot.clientErrors());
    }
}
