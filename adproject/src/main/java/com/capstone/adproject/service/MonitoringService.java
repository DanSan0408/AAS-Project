package com.capstone.adproject.service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.capstone.adproject.config.LegacyColumnCleanupMigrationRunner;
import com.capstone.adproject.config.LegacyColumnCleanupMigrationRunner.SchemaFinding;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Application-side monitoring: counts failures, remembers the most recent ones, and takes a point-in-time health
 * snapshot (database, AI service, mail, upload storage, schema, JVM) for the Super Admin monitoring page and the
 * {@link AlertingService} watcher. Counters are exposed through Micrometer as well.
 */
@Service
public class MonitoringService {

    private static final int MAX_RECENT_ERRORS = 100;

    public enum Level { UP, WARN, DOWN }

    /** One recorded server-side failure. Only the path is kept, never the query string (it can hold reset tokens). */
    public record ErrorEvent(Instant at, String errorId, int status, String method, String path,
                             String exception, String message) {
        public String atText() {
            return formatUtc(at);
        }

        /** Class name without the package, for a compact table cell. */
        public String exceptionShort() {
            int dot = exception == null ? -1 : exception.lastIndexOf('.');
            return dot < 0 || exception == null ? exception : exception.substring(dot + 1);
        }
    }

    public record Check(String name, Level level, String detail, Long latencyMs) {}

    public record Snapshot(Instant takenAt, Level overall, List<Check> checks,
                           long uptimeSeconds, long heapUsedMb, long heapMaxMb, int threads, int processors,
                           double loadAverage, long diskFreeMb, long diskTotalMb,
                           long serverErrors, long clientErrors, long schemaIncidents,
                           List<SchemaFinding> schemaFindings, List<ErrorEvent> recentErrors, String javaVersion, String revision) {
        public String takenAtText() {
            return formatUtc(takenAt);
        }

        public long heapPercent() {
            return heapMaxMb <= 0 ? 0 : Math.round(heapUsedMb * 100.0 / heapMaxMb);
        }

        public long diskUsedPercent() {
            return diskTotalMb <= 0 ? 0 : Math.round((diskTotalMb - diskFreeMb) * 100.0 / diskTotalMb);
        }

        public String uptimeText() {
            long d = uptimeSeconds / 86400;
            long h = (uptimeSeconds % 86400) / 3600;
            long m = (uptimeSeconds % 3600) / 60;
            return d > 0 ? d + "d " + h + "h " + m + "m" : h > 0 ? h + "h " + m + "m" : m + "m";
        }
    }

    public static String formatUtc(Instant instant) {
        return instant == null ? "never"
                : java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
                        .withZone(java.time.ZoneOffset.UTC).format(instant);
    }

    private final MeterRegistry meterRegistry;
    private final Counter criticalErrorCounter;
    private final Counter userSignupFailureCounter;
    private final Counter schemaIncidentCounter;
    private final Counter clientErrorCounter;
    private final Deque<ErrorEvent> recentErrors = new ConcurrentLinkedDeque<>();

    private final JdbcTemplate jdbcTemplate;
    private final AiServiceClient aiServiceClient;
    private final EmailService emailService;
    private final LegacyColumnCleanupMigrationRunner schemaGuard;
    private final String uploadDir;
    private final String revision;

    private volatile String lastSchemaIncident;

    public MonitoringService(MeterRegistry meterRegistry, JdbcTemplate jdbcTemplate, AiServiceClient aiServiceClient,
                             EmailService emailService, LegacyColumnCleanupMigrationRunner schemaGuard,
                             @Value("${app.upload.submissions-dir:./uploads/submissions}") String uploadDir,
                             @Value("${app.revision:unknown}") String revision) {
        this.revision = revision;
        this.meterRegistry = meterRegistry;
        this.jdbcTemplate = jdbcTemplate;
        this.aiServiceClient = aiServiceClient;
        this.emailService = emailService;
        this.schemaGuard = schemaGuard;
        this.uploadDir = uploadDir;

        // Naming convention: 'adproject.<metric_name>.total'
        this.criticalErrorCounter = Counter.builder("adproject.critical.errors.total")
                .description("Total count of unexpected 5xx server errors caught by GlobalExceptionHandler")
                .register(meterRegistry);
        this.userSignupFailureCounter = Counter.builder("adproject.user.signup.failures.total")
                .description("Total count of failed user signup attempts")
                .register(meterRegistry);
        this.schemaIncidentCounter = Counter.builder("adproject.schema.incidents.total")
                .description("Database errors showing the schema and the code disagree (unknown column, missing default...)")
                .register(meterRegistry);
        this.clientErrorCounter = Counter.builder("adproject.client.errors.total")
                .description("Total count of handled 4xx responses")
                .register(meterRegistry);
        Gauge.builder("adproject.recent.errors", recentErrors, Deque::size)
                .description("Number of server errors currently held in the recent-error buffer")
                .register(meterRegistry);
    }

    public void incrementCriticalErrorCount() {
        criticalErrorCounter.increment();
    }

    public void incrementUserSignupFailureCount() {
        userSignupFailureCounter.increment();
    }

    public void recordSchemaIncident(String summary) {
        lastSchemaIncident = summary;
        schemaIncidentCounter.increment();
    }

    /** Remembers a handled failure. 4xx are only counted (bots probing URLs would otherwise drown real errors). */
    public void recordError(String errorId, int status, String method, String path, Throwable cause) {
        if (status < 500) {
            clientErrorCounter.increment();
            return;
        }
        String message = cause == null || cause.getMessage() == null ? "" : cause.getMessage();
        if (message.length() > 300) {
            message = message.substring(0, 300) + "...";
        }
        recentErrors.addFirst(new ErrorEvent(Instant.now(), errorId, status, method, path,
                cause == null ? "unknown" : cause.getClass().getName(), message));
        while (recentErrors.size() > MAX_RECENT_ERRORS) {
            recentErrors.pollLast();
        }
    }

    public List<ErrorEvent> getRecentErrors() {
        return new ArrayList<>(recentErrors);
    }

    public String getLastSchemaIncident() {
        return lastSchemaIncident;
    }

    /** Runs every probe once. Each probe is isolated: one failing dependency never prevents the others reporting. */
    public Snapshot snapshot() {
        List<Check> checks = new ArrayList<>();
        checks.add(databaseCheck());
        checks.add(aiCheck());
        checks.add(mailCheck());
        checks.add(storageCheck());
        checks.add(schemaCheck());

        Level overall = Level.UP;
        for (Check check : checks) {
            if (check.level() == Level.DOWN) {
                overall = Level.DOWN;
                break;
            }
            if (check.level() == Level.WARN) {
                overall = Level.WARN;
            }
        }

        Runtime runtime = Runtime.getRuntime();
        long heapUsed = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long heapMax = runtime.maxMemory() / (1024 * 1024);
        File root = new File(uploadDir);
        while (root != null && !root.exists()) {
            root = root.getParentFile();
        }
        File diskRoot = root != null ? root : new File("/");

        return new Snapshot(Instant.now(), overall, checks,
                ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
                heapUsed, heapMax, ManagementFactory.getThreadMXBean().getThreadCount(),
                runtime.availableProcessors(), ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage(),
                diskRoot.getUsableSpace() / (1024 * 1024), diskRoot.getTotalSpace() / (1024 * 1024),
                (long) criticalErrorCounter.count(), (long) clientErrorCounter.count(),
                (long) schemaIncidentCounter.count(),
                schemaGuard == null ? List.of() : schemaGuard.getFindings(),
                getRecentErrors(), System.getProperty("java.version"), revision);
    }

    private Check databaseCheck() {
        long started = System.nanoTime();
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            long ms = (System.nanoTime() - started) / 1_000_000;
            return new Check("Database", ms > 1000 ? Level.WARN : Level.UP,
                    ms > 1000 ? "Responding slowly" : "Responding", ms);
        } catch (Exception e) {
            return new Check("Database", Level.DOWN, "Query failed: " + shorten(e.getMessage()), null);
        }
    }

    private Check aiCheck() {
        long started = System.nanoTime();
        boolean up = aiServiceClient != null && aiServiceClient.isServiceAvailable();
        long ms = (System.nanoTime() - started) / 1_000_000;
        return up ? new Check("AI service", Level.UP, "Healthy", ms)
                : new Check("AI service", Level.WARN, "Not reachable. AI features are unavailable; the rest of the system works.", ms);
    }

    private Check mailCheck() {
        String problem = emailService == null ? null : emailService.mailConfigProblem();
        return problem == null
                ? new Check("Email", Level.UP, "Configured, sending as " + emailService.effectiveFromAddress(), null)
                : new Check("Email", Level.WARN, problem, null);
    }

    private Check storageCheck() {
        File dir = new File(uploadDir);
        if (!dir.isDirectory()) {
            return new Check("Upload storage", Level.DOWN, "Upload directory is missing: " + uploadDir, null);
        }
        if (!dir.canWrite()) {
            return new Check("Upload storage", Level.DOWN, "Upload directory is not writable", null);
        }
        long freeMb = dir.getUsableSpace() / (1024 * 1024);
        if (freeMb < 500) {
            return new Check("Upload storage", Level.WARN, "Low disk space: " + freeMb + " MB free", null);
        }
        return new Check("Upload storage", Level.UP, "Writable, " + freeMb + " MB free", null);
    }

    private Check schemaCheck() {
        if (schemaGuard == null) {
            return new Check("Database schema", Level.UP, "Schema guard not active", null);
        }
        List<SchemaFinding> findings = schemaGuard.getFindings();
        long missing = findings.stream().filter(f -> "MISSING_COLUMN".equals(f.kind())).count();
        long relaxed = findings.stream().filter(f -> "LEGACY_RELAXED".equals(f.kind())).count();
        long blocked = findings.stream().filter(f -> "LEGACY_BLOCKING".equals(f.kind())).count();
        if (missing > 0 || blocked > 0) {
            return new Check("Database schema", Level.DOWN,
                    missing + " expected column(s) missing, " + blocked + " legacy column(s) still blocking inserts"
                            + namesOf(findings), null);
        }
        if (lastSchemaIncident != null) {
            return new Check("Database schema", Level.WARN, "A schema fault occurred at runtime: " + lastSchemaIncident, null);
        }
        return new Check("Database schema", Level.UP,
                relaxed == 0 ? "Matches the code" : relaxed + " legacy column(s) were made optional at startup", null);
    }

    /** "Which ones": the first few table.column names, so an alert can be acted on without opening the monitoring page. */
    static String namesOf(List<SchemaFinding> findings) {
        List<String> names = findings.stream()
                .filter(f -> "MISSING_COLUMN".equals(f.kind()) || "LEGACY_BLOCKING".equals(f.kind()))
                .map(f -> f.table() + "." + f.column())
                .toList();
        if (names.isEmpty()) {
            return "";
        }
        String shown = String.join(", ", names.subList(0, Math.min(8, names.size())));
        return ": " + shown + (names.size() > 8 ? " (+" + (names.size() - 8) + " more)" : "");
    }

    private static String shorten(String message) {
        if (message == null) {
            return "";
        }
        return message.length() > 160 ? message.substring(0, 160) + "..." : message;
    }
}
