package com.capstone.adproject.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.capstone.adproject.dto.ai.AiUsageReport;
import com.capstone.adproject.model.AiUsageEvent;
import com.capstone.adproject.model.AiUsageEvent.Feature;
import com.capstone.adproject.model.AiUsageEvent.Outcome;
import com.capstone.adproject.repositories.AiUsageEventRepository;

/** What is stored for each AI request, and the figures worked out from it. */
class AiUsageServiceTest {

    private AiUsageEventRepository repo;
    private AiUsageService service;

    @BeforeEach
    void setUp() {
        repo = mock(AiUsageEventRepository.class);
        service = new AiUsageService(repo, settings(null));
        at("2026-11-23T12:00:00Z");
    }

    private static AiUsageSettingsService settings(String budget) {
        return new AiUsageSettingsService(null, budget == null ? null : new BigDecimal(budget), null);
    }

    private void at(String instant) {
        service.clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    private static AiUsageEvent event(String when, Feature feature, Outcome outcome, String user, int calls, int failed,
                                      long prompt, long cached, long completion, String cost, long latencyMs, String error) {
        AiUsageEvent e = new AiUsageEvent();
        e.setOccurredAt(LocalDateTime.parse(when));
        e.setFeature(feature);
        e.setOutcome(outcome);
        e.setRequestedBy(user);
        e.setLlmCalls(calls);
        e.setFailedCalls(failed);
        e.setPromptTokens(prompt);
        e.setCachedPromptTokens(cached);
        e.setCompletionTokens(completion);
        e.setCostUsd(cost == null ? null : new BigDecimal(cost));
        e.setLatencyMs(latencyMs);
        e.setErrorKind(error);
        e.setProvider("deepseek");
        e.setModel("deepseek-flash");
        return e;
    }

    private static AiUsageEvent ok(String when, String user, String cost) {
        return event(when, Feature.COMMENT_CHECK, Outcome.OK, user, 1, 0, 1000, 400, 200, cost, 2000, null);
    }

    private void stored(AiUsageEvent... events) {
        when(repo.findByOccurredAtGreaterThanEqualOrderByOccurredAtAsc(any())).thenReturn(List.of(events));
        when(repo.findTop25ByOrderByOccurredAtDescIdDesc()).thenReturn(List.of());
    }

    // ---- The AI service's header -----------------------------------------------------------------------------------

    @Test
    void theHeaderIsReadIntoNumbers() {
        var h = AiUsageService.parseHeader("{\"provider\":\"deepseek\",\"model\":\"deepseek-flash\",\"calls\":2,\"failedCalls\":1,\"retries\":1,"
                + "\"promptTokens\":1000,\"cachedPromptTokens\":400,\"completionTokens\":200,\"reasoningTokens\":0,\"modelMs\":1800,"
                + "\"costUsd\":0.000212,\"errors\":[\"HTTP429\"]}");
        assertNotNull(h);
        assertEquals("deepseek-flash", h.model());
        assertEquals(2, h.calls());
        assertEquals(1000, h.promptTokens());
        assertEquals(400, h.cachedPromptTokens());
        assertEquals(0, new BigDecimal("0.000212").compareTo(h.costUsd()));
        assertEquals(List.of("HTTP429"), h.errors());
    }

    @Test
    void aHeaderThatIsMissingOrNotWhatWasExpectedIsIgnoredNotFatal() {
        assertNull(AiUsageService.parseHeader(null));
        assertNull(AiUsageService.parseHeader(""));
        assertNull(AiUsageService.parseHeader("not json"));
        assertNull(AiUsageService.parseHeader("[1,2]"));
        assertNull(AiUsageService.parseHeader("x".repeat(5000)));
    }

    @Test
    void oddNumbersAreClampedAndAnUnknownOrNegativeCostBecomesUnpriced() {
        var h = AiUsageService.parseHeader("{\"calls\":-3,\"promptTokens\":99999999999999,\"costUsd\":-1,"
                + "\"errors\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"" + "k".repeat(200) + "\"]}");
        assertEquals(0, h.calls());
        assertEquals(10_000_000_000L, h.promptTokens());
        assertNull(h.costUsd());
        assertEquals(5, h.errors().size());
        assertNull(AiUsageService.parseHeader("{\"costUsd\":null}").costUsd());
        assertNull(AiUsageService.parseHeader("{\"costUsd\":\"cheap\"}").costUsd());
    }

    @Test
    void aStatusMapsToHowTheRequestEnded() {
        assertEquals(Outcome.OK, AiUsageService.outcomeOf("OK"));
        assertEquals(Outcome.PARTIAL, AiUsageService.outcomeOf("PARTIAL"));
        assertEquals(Outcome.UNAVAILABLE, AiUsageService.outcomeOf("UNAVAILABLE"));
        assertEquals(Outcome.OK, AiUsageService.outcomeOf(null));
        assertNull(AiUsageService.outcomeOf("NOT_USED"));
    }

    // ---- Saving ------------------------------------------------------------------------------------------------------

    @Test
    void aRequestIsSavedWithItsNumbersAndTheUnauthenticatedCallerBecomesSystem() {
        var header = AiUsageService.parseHeader("{\"provider\":\"deepseek\",\"model\":\"deepseek-flash\",\"calls\":1,"
                + "\"promptTokens\":500,\"cachedPromptTokens\":100,\"completionTokens\":50,\"costUsd\":0.0001,\"errors\":[]}");
        service.record(Feature.TEAM_AUDIT, Outcome.OK, "anonymous", header, 1500, null);

        var saved = ArgumentCaptor.forClass(AiUsageEvent.class);
        verify(repo).save(saved.capture());
        AiUsageEvent e = saved.getValue();
        assertEquals("system", e.getRequestedBy());
        assertEquals(LocalDateTime.parse("2026-11-23T12:00:00"), e.getOccurredAt());
        assertEquals(500, e.getPromptTokens());
        assertEquals(1500, e.getLatencyMs());
        assertEquals(Feature.TEAM_AUDIT, e.getFeature());
        assertNull(e.getErrorKind());
    }

    @Test
    void aRequestWithNoModelCallIsSavedWithZeroesAndTheFailureKindsAreJoinedAndShortened() {
        var header = AiUsageService.parseHeader("{\"calls\":1,\"failedCalls\":1,\"errors\":[\"HTTP402\"]}");
        service.record(Feature.COMMENT_CHECK, Outcome.UNAVAILABLE, "u".repeat(300), header, -5, "ReadTimeout");
        service.record(Feature.COMMENT_CHECK, Outcome.CACHE_HIT, "admin1", null, 2, null);

        var saved = ArgumentCaptor.forClass(AiUsageEvent.class);
        verify(repo, org.mockito.Mockito.times(2)).save(saved.capture());
        AiUsageEvent first = saved.getAllValues().get(0);
        assertEquals(100, first.getRequestedBy().length());
        assertEquals(0, first.getLatencyMs());
        assertEquals("ReadTimeout, HTTP402", first.getErrorKind());
        AiUsageEvent second = saved.getAllValues().get(1);
        assertEquals(0, second.getLlmCalls());
        assertNull(second.getCostUsd());
    }

    // ---- Figures -----------------------------------------------------------------------------------------------------

    @Test
    void anEmptyHistoryGivesZeroesNotErrors() {
        stored();
        AiUsageReport r = service.report(30);
        assertEquals(0, r.period().requests());
        assertEquals(30, r.perDay().size());
        assertEquals(0, r.latency().samples());
        assertNull(r.period().okPercent());
        assertNull(r.period().cachedPercent());
        assertNull(r.period().costPerRequestText());
        assertNull(r.forecast().burnText());
        assertEquals("$0.0000", r.period().costText());
    }

    @Test
    void totalsSplitTodayThisMonthAndThePeriodAndCountTokensAndCost() {
        stored(ok("2026-10-30T10:00:00", "admin1", "1.00"),        // last month: only in a 30-day period
                ok("2026-11-02T10:00:00", "admin1", "0.50"),
                ok("2026-11-23T08:00:00", "admin2", "0.25"));
        AiUsageReport r = service.report(30);
        assertEquals(3, r.period().requests());
        assertEquals(0, new BigDecimal("1.75").compareTo(r.period().costUsd()));
        assertEquals(2, r.monthToDate().requests());
        assertEquals(0, new BigDecimal("0.75").compareTo(r.monthToDate().costUsd()));
        assertEquals(1, r.today().requests());
        assertEquals(3600, r.period().totalTokens());
        assertEquals(40, r.period().cachedPercent());
        assertEquals(100, r.period().okPercent());
    }

    @Test
    void theShortPeriodStillSeesTheWholeMonthForMonthToDate() {
        stored(ok("2026-11-02T10:00:00", "admin1", "0.50"), ok("2026-11-22T10:00:00", "admin1", "0.25"));
        AiUsageReport r = service.report(7);
        assertEquals(1, r.period().requests());          // only the last seven days
        assertEquals(2, r.monthToDate().requests());     // but the month is the month
        assertEquals(7, r.perDay().size());
    }

    @Test
    void theFirstDayOfThePeriodIsIncludedAndThePreviousSecondIsNot() {
        stored(ok("2026-11-17T00:00:00", "a", "0.10"), ok("2026-11-16T23:59:59", "a", "0.10"));
        AiUsageReport r = service.report(7);              // 17th to 23rd
        assertEquals(1, r.period().requests());
        assertEquals(LocalDateTime.parse("2026-11-17T00:00:00").toLocalDate(), r.from());
    }

    @Test
    void theNumberOfDaysIsKeptWithinWhatCanBeShown() {
        stored();
        assertEquals(1, service.report(0).perDay().size());
        assertEquals(1, service.report(-4).perDay().size());
        assertEquals(AiUsageService.MAX_DAYS, service.report(100_000).perDay().size());
    }

    @Test
    void everyDayInThePeriodAppearsEvenWhenNothingHappenedAndTheLastIsToday() {
        stored(ok("2026-11-20T10:00:00", "a", "0.10"));
        AiUsageReport r = service.report(7);
        assertEquals(7, r.perDay().size());
        assertEquals(r.to(), r.perDay().get(6).date());
        assertEquals(0, r.perDay().get(6).totals().requests());
        assertEquals(1, r.perDay().stream().filter(d -> d.totals().requests() > 0).count());
    }

    @Test
    void requestsWithoutAPriceCountTheirTokensButNotAnInventedCost() {
        stored(ok("2026-11-23T08:00:00", "a", null), ok("2026-11-23T09:00:00", "a", "0.20"));
        AiUsageReport r = service.report(7);
        assertEquals(1, r.period().unpricedRequests());
        assertEquals(0, new BigDecimal("0.20").compareTo(r.period().costUsd()));
        assertEquals(2400, r.period().totalTokens());
    }

    @Test
    void savedRequestsCostNothingAndAreNotCountedAsAnsweredOrFailed() {
        stored(ok("2026-11-23T08:00:00", "a", "0.10"),
                event("2026-11-23T08:01:00", Feature.COMMENT_CHECK, Outcome.CACHE_HIT, "a", 0, 0, 0, 0, 0, null, 3, null),
                event("2026-11-23T08:02:00", Feature.COMMENT_CHECK, Outcome.RATE_LIMITED, "a", 0, 0, 0, 0, 0, null, 1, null),
                event("2026-11-23T08:03:00", Feature.COMMENT_CHECK, Outcome.SERVICE_ERROR, "a", 0, 0, 0, 0, 0, null, 900, "HTTP 500"));
        var t = service.report(7).period();
        assertEquals(4, t.requests());
        assertEquals(2, t.attempted());          // the answered one and the service error
        assertEquals(1, t.cacheHits());
        assertEquals(1, t.rateLimited());
        assertEquals(50, t.okPercent());
        assertEquals(1, t.modelRequests());
    }

    @Test
    void speedIsMeasuredOnlyOverRequestsThatReachedTheModelAndGotAnAnswer() {
        stored(event("2026-11-23T08:00:00", Feature.COMMENT_CHECK, Outcome.OK, "a", 1, 0, 1, 0, 1, "0.1", 1000, null),
                event("2026-11-23T08:01:00", Feature.COMMENT_CHECK, Outcome.OK, "a", 1, 0, 1, 0, 1, "0.1", 2000, null),
                event("2026-11-23T08:02:00", Feature.COMMENT_CHECK, Outcome.OK, "a", 1, 0, 1, 0, 1, "0.1", 3000, null),
                event("2026-11-23T08:03:00", Feature.COMMENT_CHECK, Outcome.OK, "a", 1, 0, 1, 0, 1, "0.1", 4000, null),
                event("2026-11-23T08:04:00", Feature.COMMENT_CHECK, Outcome.CACHE_HIT, "a", 0, 0, 0, 0, 0, null, 2, null),
                event("2026-11-23T08:05:00", Feature.COMMENT_CHECK, Outcome.SERVICE_ERROR, "a", 0, 0, 0, 0, 0, null, 60000, "ReadTimeout"));
        var latency = service.report(7).latency();
        assertEquals(4, latency.samples());
        assertEquals(2500, latency.avgMs());
        assertEquals(2000, latency.p50Ms());
        assertEquals(4000, latency.p95Ms());
        assertEquals(4000, latency.maxMs());
    }

    @Test
    void thePercentileIsTheNearestRank() {
        var times = java.util.stream.LongStream.rangeClosed(1, 100).boxed().toList();
        assertEquals(50, AiUsageService.percentile(times, 50));
        assertEquals(95, AiUsageService.percentile(times, 95));
        assertEquals(7, AiUsageService.percentile(List.of(7L), 95));
    }

    @Test
    void peopleAndFeaturesAreRankedByWhatTheyCost() {
        stored(ok("2026-11-23T08:00:00", "cheap", "0.01"), ok("2026-11-23T08:01:00", "pricey", "0.90"),
                ok("2026-11-23T08:02:00", "pricey", "0.10"), ok("2026-11-23T08:03:00", "anonymous-free", null));
        var byUser = service.report(7).byUser();
        assertEquals("pricey", byUser.get(0).label());
        assertEquals(2, byUser.get(0).totals().requests());
        assertEquals("cheap", byUser.get(1).label());
    }

    @Test
    void modelsAreListedByTheirNameAndOnlyRequestsThatCalledTheModelCount() {
        var unknown = ok("2026-11-23T08:02:00", "a", "0.10");
        unknown.setModel(null);
        var cached = event("2026-11-23T08:03:00", Feature.COMMENT_CHECK, Outcome.CACHE_HIT, "a", 0, 0, 0, 0, 0, null, 2, null);
        stored(ok("2026-11-23T08:00:00", "a", "0.10"), ok("2026-11-23T08:01:00", "a", "0.10"), unknown, cached);
        var byModel = service.report(7).byModel();
        assertEquals(List.of("deepseek-flash", "deepseek (unknown model)"), byModel.stream().map(AiUsageReport.Row::label).toList());
        assertEquals(2, byModel.get(0).totals().llmCalls());
    }

    @Test
    void failuresAreGroupedByKindMostFrequentFirstWithTheLastTimeSeen() {
        stored(event("2026-11-22T08:00:00", Feature.COMMENT_CHECK, Outcome.UNAVAILABLE, "a", 1, 1, 0, 0, 0, "0", 10, "HTTP402"),
                event("2026-11-23T08:00:00", Feature.COMMENT_CHECK, Outcome.UNAVAILABLE, "a", 1, 1, 0, 0, 0, "0", 10, "HTTP402"),
                event("2026-11-23T09:00:00", Feature.COMMENT_CHECK, Outcome.SERVICE_ERROR, "a", 0, 0, 0, 0, 0, null, 10, "ReadTimeout"));
        var failures = service.report(7).failures();
        assertEquals("HTTP402", failures.get(0).kind());
        assertEquals(2, failures.get(0).count());
        assertEquals(LocalDateTime.parse("2026-11-23T08:00:00"), failures.get(0).lastAt());
        assertEquals("ReadTimeout", failures.get(1).kind());
    }

    // ---- Where the month is heading ----------------------------------------------------------------------------------

    @Test
    void theMonthIsProjectedFromWhatHasBeenSpentSoFar() {
        at("2026-11-10T00:00:00Z");                        // nine days gone of a 30-day month
        stored(ok("2026-11-03T10:00:00", "a", "3.00"));
        var f = service.report(30).forecast();
        assertEquals(0, new BigDecimal("10.000000").compareTo(f.projectedMonthUsd()));
    }

    @Test
    void noProjectionIsMadeFromLessThanADayOfData() {
        at("2026-11-01T10:00:00Z");
        stored(ok("2026-11-01T09:00:00", "a", "3.00"));
        assertNull(service.report(30).forecast().projectedText());
    }

    @Test
    void theBudgetShowsHowMuchIsUsedAndIsAbsentWhenNotSet() {
        service = new AiUsageService(repo, settings("20"));
        at("2026-11-23T12:00:00Z");
        stored(ok("2026-11-10T10:00:00", "a", "5.00"));
        var f = service.report(30).forecast();
        assertTrue(f.hasBudget());
        assertEquals(25, f.budgetUsedPercent());
        assertEquals("$20.00", f.budgetText());

        for (String off : new String[] {null, "0", "-3"}) {
            var none = new AiUsageService(repo, settings(off));
            none.clock = service.clock;
            assertFalse(none.report(30).forecast().hasBudget());
            assertNull(none.monthlyBudgetUsd());
        }
    }

    @Test
    void theDailyBurnIsTheLastSevenDaysSpentDividedBySeven() {
        stored(ok("2026-11-20T10:00:00", "a", "0.70"), ok("2026-11-01T10:00:00", "a", "9.00"));
        assertEquals(0, new BigDecimal("0.1").compareTo(service.report(30).forecast().dailyBurnUsd()));
    }

    @Test
    void moneyIsShownWithTheDecimalsThatMakeASmallAmountVisible() {
        assertEquals("$12.35", AiUsageReport.usd(new BigDecimal("12.345")));
        assertEquals("$0.123", AiUsageReport.usd(new BigDecimal("0.1234")));
        assertEquals("$0.0012", AiUsageReport.usd(new BigDecimal("0.001234")));
        assertEquals("n/a", AiUsageReport.usd(null));
    }
}
