package com.capstone.adproject.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.capstone.adproject.dto.ai.AiUsageReport;

/**
 * Warns before the AI money runs out instead of after: when the month's estimated spend passes 80% and 100% of
 * {@code AI_MONTHLY_BUDGET_USD}, and when the provider balance drops below {@code AI_LOW_BALANCE_ALERT}. Both are off while their
 * setting is 0 or empty. Each warning goes out once (the spend ones once per month; the balance one again only after a top-up
 * brings it back above the line), through the same email and Telegram channels as every other alert.
 *
 * <p>It also deletes usage records older than {@link AiUsageService#KEEP_DAYS} days, once a day.
 */
@Component
public class AiBudgetWatcher {

    private static final Logger log = LoggerFactory.getLogger(AiBudgetWatcher.class);

    private final AiUsageService usage;
    private final AiServiceClient client;
    private final AlertingService alerting;
    private final AiUsageSettingsService settings;
    private final Set<String> alreadySent = new HashSet<>();
    private boolean lowBalanceSent;
    private BigDecimal lowBalanceSentFor;
    private LocalDate lastPurge;
    Clock clock = Clock.systemUTC();

    public AiBudgetWatcher(AiUsageService usage, AiServiceClient client, AlertingService alerting, AiUsageSettingsService settings) {
        this.usage = usage;
        this.client = client;
        this.alerting = alerting;
        this.settings = settings;
    }

    @Scheduled(fixedDelayString = "${app.ai.watch-interval-ms:600000}", initialDelayString = "${app.ai.watch-initial-delay-ms:120000}")
    public void check() {
        try {
            checkSpend();
            checkBalance();
            purgeOnceADay();
        } catch (Exception e) {
            log.warn("AI budget check failed ({})", e.getClass().getSimpleName());
        }
    }

    void checkSpend() {
        BigDecimal budget = usage.monthlyBudgetUsd();
        if (budget == null) return;
        BigDecimal spent = usage.spentThisMonth();
        // The budget is part of the key: raising it after a warning means the new, higher line can warn again.
        String month = LocalDate.now(clock.withZone(ZoneOffset.UTC)).toString().substring(0, 7) + ":" + budget.toPlainString();
        // The higher line first: crossing 100% at once should not also send the 80% message.
        if (spent.compareTo(budget) >= 0) {
            if (alreadySent.add("budget100:" + month)) {
                alreadySent.add("budget80:" + month);
                alerting.notifyAll("[AI budget] Monthly AI budget used up",
                        "The estimated AI spend this month is " + AiUsageReport.usd(spent) + ", at or above the monthly budget of "
                                + AiUsageReport.usd(budget) + ". The AI keeps working; raise AI_MONTHLY_BUDGET_USD or look at the AI usage page.");
            }
        } else if (spent.compareTo(budget.multiply(new BigDecimal("0.8"))) >= 0 && alreadySent.add("budget80:" + month)) {
            alerting.notifyAll("[AI budget] 80% of the monthly AI budget used",
                    "The estimated AI spend this month is " + AiUsageReport.usd(spent) + " of a " + AiUsageReport.usd(budget)
                            + " budget. See the AI usage page for what is using it.");
        }
    }

    void checkBalance() {
        BigDecimal lowBalance = settings.lowBalanceAlert();
        if (lowBalance == null) return;
        if (!lowBalance.equals(lowBalanceSentFor)) {   // a new level starts afresh
            lowBalanceSent = false;
            lowBalanceSentFor = lowBalance;
        }
        AiServiceClient.Balance balance = client.providerBalance();
        if (!balance.supported() || balance.error() != null || balance.balances() == null || balance.balances().isEmpty()) return;
        AiServiceClient.Amount first = balance.balances().get(0);
        if (first.total() == null) return;
        boolean low = first.total() < lowBalance.doubleValue() || Boolean.FALSE.equals(balance.available());
        if (low && !lowBalanceSent) {
            lowBalanceSent = true;
            alerting.notifyAll("[AI budget] AI account balance is low",
                    "The " + balance.provider() + " balance is " + first.total() + " " + first.currency() + ", below the alert level of "
                            + lowBalance.toPlainString() + ". AI features fall back to calculated results when it runs out. Top up the account.");
        } else if (!low) {
            lowBalanceSent = false;   // topped up: the next drop warns again
        }
    }

    void purgeOnceADay() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (today.equals(lastPurge)) return;
        lastPurge = today;
        long removed = usage.purgeOld();
        if (removed > 0) log.info("Deleted {} AI usage records older than {} days", removed, AiUsageService.KEEP_DAYS);
    }
}
