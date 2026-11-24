package com.capstone.adproject.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.dto.ai.AiUsageReport;
import com.capstone.adproject.dto.ai.AiUsageReport.Totals;
import com.capstone.adproject.model.AiUsageEvent;
import com.capstone.adproject.model.AiUsageEvent.Feature;
import com.capstone.adproject.model.AiUsageEvent.Outcome;
import com.capstone.adproject.repositories.AiUsageEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Remembers what every AI request used and turns it into the figures on the Super Admin "AI usage" page.
 *
 * <p>The AI service reports each request's tokens, time and estimated cost in a response header; the app adds who asked and how
 * the request ended. Nothing the model was given or answered is kept. All times are UTC.
 */
@Service
public class AiUsageService {

    /** Name of the response header the AI service puts its summary in. */
    public static final String USAGE_HEADER = "X-AI-Usage";
    /** The longest period the page can show, which is also how far back the report reads. */
    public static final int MAX_DAYS = 90;
    /** Requests older than this are deleted by the daily clean-up. */
    public static final int KEEP_DAYS = 400;

    private static final ObjectMapper JSON = new ObjectMapper();

    /** The AI service's summary of one request, read from {@link #USAGE_HEADER}. Every number is clamped to something sane. */
    public record UsageHeader(String provider, String model, int calls, int failedCalls, int retries, long promptTokens,
                              long cachedPromptTokens, long completionTokens, long reasoningTokens, BigDecimal costUsd,
                              List<String> errors) {}

    private final AiUsageEventRepository events;
    private final AiUsageSettingsService settings;
    Clock clock = Clock.systemUTC();

    public AiUsageService(AiUsageEventRepository events, AiUsageSettingsService settings) {
        this.events = events;
        this.settings = settings;
    }

    /** The monthly budget in force (set on the AI usage page, else the server default); null when there is none. */
    public BigDecimal monthlyBudgetUsd() {
        return settings.monthlyBudgetUsd();
    }

    // ---- Recording -------------------------------------------------------------------------------------------------

    /** The outcome for an {@code aiStatus} the AI service returned; null when the AI was not needed (nothing to record). */
    public static Outcome outcomeOf(String aiStatus) {
        if (aiStatus == null) return Outcome.OK;
        return switch (aiStatus) {
            case "OK" -> Outcome.OK;
            case "PARTIAL" -> Outcome.PARTIAL;
            case "UNAVAILABLE" -> Outcome.UNAVAILABLE;
            case "NOT_USED" -> null;
            default -> Outcome.OK;
        };
    }

    /**
     * Saves one request. Runs in its own transaction so a failure here can never mark the caller's transaction for rollback;
     * callers also catch everything, because usage figures must never break an AI feature.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Feature feature, Outcome outcome, String requestedBy, UsageHeader usage, long latencyMs, String errorKind) {
        AiUsageEvent e = new AiUsageEvent();
        e.setOccurredAt(LocalDateTime.now(clock.withZone(ZoneOffset.UTC)));
        e.setFeature(feature);
        e.setOutcome(outcome);
        e.setRequestedBy(cut(requestedBy == null || requestedBy.isBlank() || "anonymous".equals(requestedBy) ? "system" : requestedBy, 100));
        e.setLatencyMs(Math.max(0, latencyMs));
        List<String> kinds = new ArrayList<>();
        if (errorKind != null && !errorKind.isBlank()) kinds.add(errorKind);
        if (usage != null) {
            e.setProvider(cut(usage.provider(), 20));
            e.setModel(cut(usage.model(), 80));
            e.setLlmCalls(usage.calls());
            e.setFailedCalls(usage.failedCalls());
            e.setRetries(usage.retries());
            e.setPromptTokens(usage.promptTokens());
            e.setCachedPromptTokens(usage.cachedPromptTokens());
            e.setCompletionTokens(usage.completionTokens());
            e.setReasoningTokens(usage.reasoningTokens());
            e.setCostUsd(usage.costUsd());
            kinds.addAll(usage.errors());
        }
        if (!kinds.isEmpty()) e.setErrorKind(cut(String.join(", ", kinds), 100));
        events.save(e);
    }

    /** Reads the AI service's header; null when it is absent or not what was expected. Never throws. */
    public static UsageHeader parseHeader(String value) {
        if (value == null || value.isBlank() || value.length() > 4000) return null;
        try {
            JsonNode n = JSON.readTree(value);
            if (n == null || !n.isObject()) return null;
            List<String> errors = new ArrayList<>();
            if (n.path("errors").isArray()) {
                for (JsonNode kind : n.path("errors")) {
                    if (kind.isTextual() && !kind.asText().isBlank() && errors.size() < 5) errors.add(cut(kind.asText().trim(), 40));
                }
            }
            BigDecimal cost = n.path("costUsd").isNumber() && n.path("costUsd").decimalValue().signum() >= 0
                    ? n.path("costUsd").decimalValue().setScale(8, RoundingMode.HALF_UP) : null;
            return new UsageHeader(cut(n.path("provider").asText(""), 20), cut(n.path("model").asText(""), 80),
                    (int) clamp(n.path("calls").asLong(0), 10_000), (int) clamp(n.path("failedCalls").asLong(0), 10_000),
                    (int) clamp(n.path("retries").asLong(0), 10_000), clamp(n.path("promptTokens").asLong(0), 10_000_000_000L),
                    clamp(n.path("cachedPromptTokens").asLong(0), 10_000_000_000L),
                    clamp(n.path("completionTokens").asLong(0), 10_000_000_000L),
                    clamp(n.path("reasoningTokens").asLong(0), 10_000_000_000L), cost, List.copyOf(errors));
        } catch (Exception e) {
            return null;
        }
    }

    private static long clamp(long value, long max) {
        return Math.max(0, Math.min(value, max));
    }

    private static String cut(String value, int max) {
        return value == null ? null : value.length() <= max ? value : value.substring(0, max);
    }

    // ---- Reporting -------------------------------------------------------------------------------------------------

    /** The figures for the last {@code days} days (1 to {@value #MAX_DAYS}), counting today. */
    @Transactional(readOnly = true)
    public AiUsageReport report(int days) {
        int span = Math.max(1, Math.min(days, MAX_DAYS));
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
        LocalDate today = now.toLocalDate();
        LocalDate first = today.minusDays(span - 1L);
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDateTime sevenDaysAgo = now.minusDays(7);
        LocalDateTime readFrom = List.of(first.atStartOfDay(), monthStart.atStartOfDay(), sevenDaysAgo).stream()
                .min(Comparator.naturalOrder()).orElseThrow();
        List<AiUsageEvent> all = events.findByOccurredAtGreaterThanEqualOrderByOccurredAtAsc(readFrom);

        List<AiUsageEvent> inPeriod = all.stream().filter(e -> !e.getOccurredAt().toLocalDate().isBefore(first)).toList();
        List<AiUsageEvent> inMonth = all.stream().filter(e -> !e.getOccurredAt().toLocalDate().isBefore(monthStart)).toList();
        List<AiUsageEvent> inToday = all.stream().filter(e -> e.getOccurredAt().toLocalDate().equals(today)).toList();

        List<AiUsageReport.Day> perDay = new ArrayList<>();
        Map<LocalDate, List<AiUsageEvent>> byDate = new LinkedHashMap<>();
        inPeriod.forEach(e -> byDate.computeIfAbsent(e.getOccurredAt().toLocalDate(), d -> new ArrayList<>()).add(e));
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            perDay.add(new AiUsageReport.Day(d, Totals.of(byDate.getOrDefault(d, List.of()))));
        }

        return new AiUsageReport(span, first, today, Totals.of(inPeriod), Totals.of(inToday), Totals.of(inMonth),
                forecast(all, inMonth, now, today, sevenDaysAgo, monthlyBudgetUsd()), latency(inPeriod), perDay,
                rows(inPeriod, e -> e.getFeature().name()),
                rows(inPeriod, e -> e.getRequestedBy() == null ? "unknown" : e.getRequestedBy()),
                rows(inPeriod.stream().filter(e -> e.getLlmCalls() > 0).toList(),
                        e -> e.getModel() != null ? e.getModel() : (e.getProvider() == null ? "unknown model" : e.getProvider() + " (unknown model)")),
                failures(inPeriod), events.findTop25ByOrderByOccurredAtDescIdDesc());
    }

    private AiUsageReport.Forecast forecast(List<AiUsageEvent> all, List<AiUsageEvent> inMonth, LocalDateTime now, LocalDate today,
                                            LocalDateTime sevenDaysAgo, BigDecimal monthlyBudgetUsd) {
        BigDecimal spent = Totals.of(inMonth).costUsd();
        double elapsedDays = Duration.between(today.withDayOfMonth(1).atStartOfDay(), now).toSeconds() / 86_400.0;
        // Fewer than a day of data would swing wildly, so there is no projection until there is a day to go on.
        BigDecimal projected = elapsedDays < 1 ? null
                : spent.multiply(BigDecimal.valueOf(YearMonth.from(today).lengthOfMonth() / elapsedDays)).setScale(6, RoundingMode.HALF_UP);
        List<AiUsageEvent> lastWeek = all.stream().filter(e -> !e.getOccurredAt().isBefore(sevenDaysAgo)).toList();
        BigDecimal burn = lastWeek.isEmpty() ? null : Totals.of(lastWeek).costUsd().divide(BigDecimal.valueOf(7), 8, RoundingMode.HALF_UP);
        Integer percent = monthlyBudgetUsd == null ? null
                : spent.multiply(BigDecimal.valueOf(100)).divide(monthlyBudgetUsd, 0, RoundingMode.HALF_UP).intValue();
        return new AiUsageReport.Forecast(projected, burn, monthlyBudgetUsd, percent);
    }

    private static AiUsageReport.Latency latency(List<AiUsageEvent> inPeriod) {
        List<Long> times = inPeriod.stream()
                .filter(e -> e.getLlmCalls() > 0 && e.getOutcome() != Outcome.SERVICE_ERROR)
                .map(AiUsageEvent::getLatencyMs).sorted().toList();
        if (times.isEmpty()) return AiUsageReport.Latency.NONE;
        long sum = times.stream().mapToLong(Long::longValue).sum();
        return new AiUsageReport.Latency(times.size(), Math.round((double) sum / times.size()), percentile(times, 50),
                percentile(times, 95), times.get(times.size() - 1));
    }

    /** Nearest-rank percentile of an ascending list. */
    static long percentile(List<Long> ascending, int percent) {
        int index = (int) Math.ceil(percent / 100.0 * ascending.size()) - 1;
        return ascending.get(Math.max(0, Math.min(index, ascending.size() - 1)));
    }

    private static List<AiUsageReport.Row> rows(List<AiUsageEvent> list, java.util.function.Function<AiUsageEvent, String> key) {
        Map<String, List<AiUsageEvent>> groups = new LinkedHashMap<>();
        list.forEach(e -> groups.computeIfAbsent(key.apply(e), k -> new ArrayList<>()).add(e));
        return groups.entrySet().stream().map(g -> new AiUsageReport.Row(g.getKey(), Totals.of(g.getValue())))
                .sorted(Comparator.comparing((AiUsageReport.Row r) -> r.totals().costUsd()).reversed()
                        .thenComparing(Comparator.comparingInt((AiUsageReport.Row r) -> r.totals().requests()).reversed())
                        .thenComparing(AiUsageReport.Row::label))
                .limit(15).toList();
    }

    private static List<AiUsageReport.Failure> failures(List<AiUsageEvent> inPeriod) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, LocalDateTime> last = new LinkedHashMap<>();
        for (AiUsageEvent e : inPeriod) {
            if (e.getErrorKind() == null || e.getErrorKind().isBlank()) continue;
            counts.computeIfAbsent(e.getErrorKind(), k -> new int[1])[0]++;
            last.merge(e.getErrorKind(), e.getOccurredAt(), (a, b) -> a.isAfter(b) ? a : b);
        }
        return counts.entrySet().stream().map(c -> new AiUsageReport.Failure(c.getKey(), c.getValue()[0], last.get(c.getKey())))
                .sorted(Comparator.comparingInt(AiUsageReport.Failure::count).reversed().thenComparing(AiUsageReport.Failure::kind))
                .limit(10).toList();
    }

    // ---- Upkeep ----------------------------------------------------------------------------------------------------

    /** Estimated spend so far this calendar month (UTC). */
    @Transactional(readOnly = true)
    public BigDecimal spentThisMonth() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        return events.sumCostSince(today.withDayOfMonth(1).atStartOfDay());
    }

    /** Deletes requests older than {@value #KEEP_DAYS} days; returns how many. */
    @Transactional
    public long purgeOld() {
        return events.deleteByOccurredAtBefore(LocalDateTime.now(clock.withZone(ZoneOffset.UTC)).minusDays(KEEP_DAYS));
    }
}
