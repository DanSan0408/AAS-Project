package com.capstone.adproject.dto.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.capstone.adproject.model.AiUsageEvent;

/** Everything the Super Admin "AI usage" page shows, worked out from the recorded requests. */
public record AiUsageReport(int days, LocalDate from, LocalDate to, Totals period, Totals today, Totals monthToDate,
                            Forecast forecast, Latency latency, List<Day> perDay, List<Row> byFeature, List<Row> byUser,
                            List<Row> byModel, List<Failure> failures, List<AiUsageEvent> recent) {

    /** The sums over a set of requests. Cost counts only requests the model had a price for; {@code unpricedRequests} says how many did not. */
    public record Totals(int requests, int modelRequests, int attempted, int ok, int partial, int unavailable, int serviceErrors, int cacheHits,
                         int rateLimited, int llmCalls, int failedCalls, int retries, long promptTokens, long cachedPromptTokens,
                         long completionTokens, long reasoningTokens, BigDecimal costUsd, int unpricedRequests) {

        public static final Totals NONE = new Totals(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, BigDecimal.ZERO, 0);

        public static Totals of(List<AiUsageEvent> events) {
            int requests = 0, ok = 0, partial = 0, unavailable = 0, serviceErrors = 0, cacheHits = 0, rateLimited = 0;
            int llmCalls = 0, failedCalls = 0, retries = 0, unpriced = 0, modelRequests = 0;
            long prompt = 0, cached = 0, completion = 0, reasoning = 0;
            BigDecimal cost = BigDecimal.ZERO;
            for (AiUsageEvent e : events) {
                requests++;
                switch (e.getOutcome()) {
                    case OK -> ok++;
                    case PARTIAL -> partial++;
                    case UNAVAILABLE -> unavailable++;
                    case SERVICE_ERROR -> serviceErrors++;
                    case CACHE_HIT -> cacheHits++;
                    case RATE_LIMITED -> rateLimited++;
                }
                if (e.getLlmCalls() > 0) modelRequests++;
                llmCalls += e.getLlmCalls();
                failedCalls += e.getFailedCalls();
                retries += e.getRetries();
                prompt += e.getPromptTokens();
                cached += e.getCachedPromptTokens();
                completion += e.getCompletionTokens();
                reasoning += e.getReasoningTokens();
                if (e.getCostUsd() != null) cost = cost.add(e.getCostUsd());
                else if (e.getLlmCalls() > 0) unpriced++;
            }
            return new Totals(requests, modelRequests, requests - cacheHits - rateLimited, ok, partial, unavailable, serviceErrors, cacheHits,
                    rateLimited, llmCalls, failedCalls, retries, prompt, cached, completion, reasoning, cost, unpriced);
        }

        public long totalTokens() { return promptTokens + completionTokens; }

        /** Requests that went on to the model or at least to the AI service (not answered from memory, not refused). */
        public Integer okPercent() { return attempted == 0 ? null : (int) Math.round(ok * 100.0 / attempted); }

        /** The share of prompt tokens the provider served from its cache. */
        public Integer cachedPercent() { return promptTokens == 0 ? null : (int) Math.round(cachedPromptTokens * 100.0 / promptTokens); }

        public String costText() { return usd(costUsd); }

        /** What an average request that called the model cost, or null when none did. */
        public String costPerRequestText() {
            return modelRequests == 0 ? null : usd(costUsd.divide(BigDecimal.valueOf(modelRequests), 8, RoundingMode.HALF_UP));
        }
    }

    /** The month's spend so far, where it is heading, and how it compares with the budget (all null/0 when nothing is set or known). */
    public record Forecast(BigDecimal projectedMonthUsd, BigDecimal dailyBurnUsd, BigDecimal monthlyBudgetUsd, Integer budgetUsedPercent) {
        public String projectedText() { return projectedMonthUsd == null ? null : usd(projectedMonthUsd); }
        public String burnText() { return dailyBurnUsd == null ? null : usd(dailyBurnUsd); }
        public String budgetText() { return monthlyBudgetUsd == null ? null : usd(monthlyBudgetUsd); }
        public boolean hasBudget() { return monthlyBudgetUsd != null; }
    }

    /** Time the whole request took, over requests that reached the AI service and got an answer back. */
    public record Latency(int samples, long avgMs, long p50Ms, long p95Ms, long maxMs) {
        public static final Latency NONE = new Latency(0, 0, 0, 0, 0);
    }

    public record Day(LocalDate date, Totals totals) {}

    public record Row(String label, Totals totals) {}

    public record Failure(String kind, int count, LocalDateTime lastAt) {}

    /** Dollars to the precision that matters: small amounts need four places to be anything but "$0.00". */
    public static String usd(BigDecimal value) {
        if (value == null) return "n/a";
        BigDecimal abs = value.abs();
        int places = abs.compareTo(BigDecimal.ONE) >= 0 ? 2 : abs.compareTo(new BigDecimal("0.01")) >= 0 ? 3 : 4;
        return "$" + value.setScale(places, RoundingMode.HALF_UP).toPlainString();
    }
}
