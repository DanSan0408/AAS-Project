package com.capstone.adproject.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.adproject.model.AiUsageSettings;
import com.capstone.adproject.repositories.AiUsageSettingsRepository;

/**
 * The AI spending alerts (monthly budget, low-balance level), changed from the AI usage page without a redeploy.
 *
 * <p>Until someone saves them there, the server's own settings ({@code AI_MONTHLY_BUDGET_USD}, {@code AI_LOW_BALANCE_ALERT}) apply.
 * After the first save the page owns them, including "off".
 */
@Service
public class AiUsageSettingsService {

    /** The largest value accepted: a typo such as an extra three zeros is refused instead of silently disabling the alert. */
    public static final BigDecimal MAX = new BigDecimal("1000000");

    /** What is in force now, and where it came from. */
    public record View(BigDecimal monthlyBudgetUsd, BigDecimal lowBalanceAlert, boolean savedInApp,
                       LocalDateTime updatedAt, String updatedBy) {}

    private final AiUsageSettingsRepository repository;
    private final BigDecimal defaultBudget;
    private final BigDecimal defaultLowBalance;
    Clock clock = Clock.systemUTC();

    @Autowired
    public AiUsageSettingsService(AiUsageSettingsRepository repository,
                                  @Value("${app.ai.monthly-budget-usd:0}") BigDecimal defaultBudget,
                                  @Value("${app.ai.low-balance-alert:0}") BigDecimal defaultLowBalance) {
        this.repository = repository;
        this.defaultBudget = positiveOrNull(defaultBudget);
        this.defaultLowBalance = positiveOrNull(defaultLowBalance);
    }

    private static BigDecimal positiveOrNull(BigDecimal value) {
        return value == null || value.signum() <= 0 ? null : value;
    }

    @Transactional(readOnly = true)
    public View view() {
        AiUsageSettings saved = repository == null ? null : repository.findById(AiUsageSettings.ID).orElse(null);
        if (saved == null) return new View(defaultBudget, defaultLowBalance, false, null, null);
        return new View(positiveOrNull(saved.getMonthlyBudgetUsd()), positiveOrNull(saved.getLowBalanceAlert()), true,
                saved.getUpdatedAt(), saved.getUpdatedBy());
    }

    public BigDecimal monthlyBudgetUsd() {
        return view().monthlyBudgetUsd();
    }

    public BigDecimal lowBalanceAlert() {
        return view().lowBalanceAlert();
    }

    /**
     * Saves both values as typed on the page. Empty or zero turns that alert off. Anything that is not a plain non-negative number
     * up to {@link #MAX} is refused with a message safe to show, and nothing is changed.
     */
    @Transactional
    public View save(String monthlyBudget, String lowBalance, String changedBy) {
        BigDecimal budget = parse(monthlyBudget, "The monthly budget");
        BigDecimal low = parse(lowBalance, "The low-balance alert");
        AiUsageSettings row = repository.findById(AiUsageSettings.ID).orElseGet(AiUsageSettings::new);
        row.setMonthlyBudgetUsd(budget);
        row.setLowBalanceAlert(low);
        row.setUpdatedAt(LocalDateTime.now(clock.withZone(ZoneOffset.UTC)));
        row.setUpdatedBy(changedBy == null || changedBy.isBlank() ? "unknown" : changedBy.length() > 100 ? changedBy.substring(0, 100) : changedBy);
        repository.save(row);
        return new View(budget, low, true, row.getUpdatedAt(), row.getUpdatedBy());
    }

    private static BigDecimal parse(String text, String name) {
        if (text == null || text.isBlank()) return null;
        BigDecimal value;
        try {
            value = new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a number, for example 20 or 7.50. Leave it empty to turn the alert off.");
        }
        if (value.signum() < 0) throw new IllegalArgumentException(name + " cannot be negative.");
        if (value.compareTo(MAX) > 0) throw new IllegalArgumentException(name + " cannot be more than 1,000,000.");
        if (value.signum() > 0 && value.compareTo(new BigDecimal("0.01")) < 0) {
            throw new IllegalArgumentException(name + " must be at least 0.01. Leave it empty to turn the alert off.");
        }
        value = value.setScale(2, RoundingMode.HALF_UP);
        return value.signum() == 0 ? null : value;
    }
}
