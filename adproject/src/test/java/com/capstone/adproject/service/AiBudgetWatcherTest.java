package com.capstone.adproject.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.capstone.adproject.service.AiServiceClient.Amount;
import com.capstone.adproject.service.AiServiceClient.Balance;

/** Warnings before the AI money runs out: each goes out once, and nothing is sent while the setting is off. */
class AiBudgetWatcherTest {

    private AiUsageService usage;
    private AiServiceClient client;
    private AlertingService alerting;
    private AiUsageSettingsService settings;

    @BeforeEach
    void setUp() {
        usage = mock(AiUsageService.class);
        client = mock(AiServiceClient.class);
        alerting = mock(AlertingService.class);
        settings = mock(AiUsageSettingsService.class);
    }

    private AiBudgetWatcher watcher(String budget, String lowBalance) {
        when(usage.monthlyBudgetUsd()).thenReturn(budget == null ? null : new BigDecimal(budget));
        when(settings.lowBalanceAlert()).thenReturn(lowBalance == null ? null : new BigDecimal(lowBalance));
        AiBudgetWatcher w = new AiBudgetWatcher(usage, client, alerting, settings);
        w.clock = Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC);
        return w;
    }

    private void spent(String amount) {
        when(usage.spentThisMonth()).thenReturn(new BigDecimal(amount));
    }

    private void balance(Double total, Boolean available) {
        when(client.providerBalance()).thenReturn(new Balance("deepseek", true, available, List.of(new Amount("USD", total, 0.0, total)), null));
    }

    @Test
    void nothingIsSentWhileTheBudgetIsOffOrBelowEightyPercent() {
        spent("100");
        watcher(null, null).check();
        verify(alerting, never()).notifyAll(anyString(), anyString());

        AiBudgetWatcher w = watcher("20", null);
        spent("15.99");
        w.check();
        verify(alerting, never()).notifyAll(anyString(), anyString());
    }

    @Test
    void passingEightyPercentWarnsOnceAndPassingTheBudgetWarnsOnceMore() {
        AiBudgetWatcher w = watcher("20", null);
        spent("16");
        w.check();
        w.check();                                           // same month, same level: no repeat
        verify(alerting, times(1)).notifyAll(org.mockito.ArgumentMatchers.contains("80%"), anyString());

        spent("21");
        w.check();
        w.check();
        verify(alerting, times(1)).notifyAll(org.mockito.ArgumentMatchers.contains("used up"), anyString());
    }

    @Test
    void goingStraightPastTheBudgetSendsOneMessageNotTwo() {
        AiBudgetWatcher w = watcher("20", null);
        spent("25");
        w.check();
        w.check();
        verify(alerting, times(1)).notifyAll(anyString(), anyString());
        verify(alerting, never()).notifyAll(org.mockito.ArgumentMatchers.contains("80%"), anyString());
    }

    @Test
    void raisingTheBudgetAfterAWarningLetsTheNewHigherLineWarnAgain() {
        AiBudgetWatcher w = watcher("20", null);
        spent("21");
        w.check();                                            // used up at 20
        when(usage.monthlyBudgetUsd()).thenReturn(new BigDecimal("30"));
        w.check();                                            // 21 of 30: below 80% of 30? 24 is the line, so quiet
        spent("31");
        w.check();                                            // past the new budget
        verify(alerting, times(2)).notifyAll(org.mockito.ArgumentMatchers.contains("used up"), anyString());
    }

    @Test
    void changingTheLowBalanceLevelReChecksInsteadOfStayingQuietBecauseOfTheOldWarning() {
        AiBudgetWatcher w = watcher(null, "5");
        balance(3.0, true);
        w.check();                                            // warned at level 5
        w.check();
        when(settings.lowBalanceAlert()).thenReturn(new BigDecimal("10"));
        w.check();                                            // 3 is below the new level 10: a fresh warning
        verify(alerting, times(2)).notifyAll(org.mockito.ArgumentMatchers.contains("balance is low"), anyString());
    }

    @Test
    void aNewMonthWarnsAgain() {
        AiBudgetWatcher w = watcher("20", null);
        spent("25");
        w.check();
        w.clock = Clock.fixed(Instant.parse("2026-12-02T12:00:00Z"), ZoneOffset.UTC);
        w.check();
        verify(alerting, times(2)).notifyAll(anyString(), anyString());
    }

    @Test
    void aLowBalanceWarnsOnceAndAgainOnlyAfterATopUp() {
        AiBudgetWatcher w = watcher(null, "5");
        balance(3.0, true);
        w.check();
        w.check();
        verify(alerting, times(1)).notifyAll(org.mockito.ArgumentMatchers.contains("balance is low"), anyString());

        balance(50.0, true);                                  // topped up
        w.check();
        balance(2.0, true);
        w.check();
        verify(alerting, times(2)).notifyAll(org.mockito.ArgumentMatchers.contains("balance is low"), anyString());
    }

    @Test
    void anAccountThatIsOutOfFundsWarnsEvenIfTheNumberLooksFine() {
        AiBudgetWatcher w = watcher(null, "5");
        balance(100.0, false);
        w.check();
        verify(alerting, times(1)).notifyAll(org.mockito.ArgumentMatchers.contains("balance is low"), anyString());
    }

    @Test
    void aBalanceThatCannotBeReadOrIsNotSupportedNeverWarnsAndTheLowBalanceCheckIsOffByDefault() {
        AiBudgetWatcher w = watcher(null, "5");
        when(client.providerBalance()).thenReturn(new Balance("deepseek", true, null, List.of(), "HTTP 401"));
        w.check();
        when(client.providerBalance()).thenReturn(new Balance("gemini", false, null, List.of(), null));
        w.check();
        AiBudgetWatcher off = watcher(null, null);
        off.check();
        verify(alerting, never()).notifyAll(anyString(), anyString());
        verify(client, times(2)).providerBalance();           // only the watcher that has a level asked
    }

    @Test
    void aFailureInsideTheCheckIsSwallowedSoTheSchedulerKeepsRunning() {
        AiBudgetWatcher w = watcher("20", null);
        when(usage.spentThisMonth()).thenThrow(new IllegalStateException("db down"));
        w.check();   // must not throw
    }

    @Test
    void oldRecordsAreDeletedOnceADay() {
        AiBudgetWatcher w = watcher(null, null);
        w.check();
        w.check();
        verify(usage, times(1)).purgeOld();
        w.clock = Clock.fixed(Instant.parse("2026-11-24T00:30:00Z"), ZoneOffset.UTC);
        w.check();
        verify(usage, times(2)).purgeOld();
    }
}
