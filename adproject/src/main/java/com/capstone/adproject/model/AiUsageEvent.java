package com.capstone.adproject.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * One request the app made (or was stopped from making) to the AI service, for the Super Admin "AI usage" page.
 *
 * <p>Only numbers and the kind of failure are kept: never a prompt, a comment, a student's name or a response, so this table can
 * be read without exposing anything the AI was given. Times are UTC, the same clock the provider's peak-hour pricing uses.
 */
@Entity
@Table(name = "ai_usage_event", indexes = @Index(name = "idx_ai_usage_event_time", columnList = "occurredAt"))
public class AiUsageEvent {

    /** Which AI feature asked. */
    public enum Feature { TEAM_AUDIT, MODERATION_BRIEFING, COMMENT_CHECK }

    /**
     * How the request ended. CACHE_HIT and RATE_LIMITED never reach the model, so they cost nothing: they are kept to show what the
     * cache and the per-person limit saved. UNAVAILABLE means the AI service answered but the model did not (the page fell back to
     * calculated results); SERVICE_ERROR means the AI service itself could not be reached or failed.
     */
    public enum Outcome { OK, PARTIAL, UNAVAILABLE, SERVICE_ERROR, CACHE_HIT, RATE_LIMITED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** UTC. */
    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Feature feature;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Outcome outcome;

    /** The signed-in username, or "system" for the scheduled comment scan. */
    @Column(length = 100)
    private String requestedBy;

    @Column(length = 40)
    private String provider;

    @Column(length = 80)
    private String model;

    /** Compact JSON list of per-model stats when a single request used more than one model (e.g. Jev + DeepSeek). */
    @Column(length = 2000)
    private String modelBreakdownJson;

    /** Calls made to the model for this request (a request can make several). */
    @Column(nullable = false)
    private int llmCalls;

    @Column(nullable = false)
    private int failedCalls;

    @Column(nullable = false)
    private int retries;

    @Column(nullable = false)
    private long promptTokens;

    /** The part of the prompt the provider served from its cache, which is billed at a much lower rate. */
    @Column(nullable = false)
    private long cachedPromptTokens;

    @Column(nullable = false)
    private long completionTokens;

    @Column(nullable = false)
    private long reasoningTokens;

    /** Estimated US dollars; null when the model has no known price (its tokens are still counted). */
    @Column(precision = 14, scale = 8)
    private BigDecimal costUsd;

    /** Wall-clock time of the whole request as the app saw it. */
    @Column(nullable = false)
    private long latencyMs;

    /** The kind of failure ("HTTP402", "ReadTimeout"), never a message. */
    @Column(length = 100)
    private String errorKind;

    public Long getId() { return id; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public Feature getFeature() { return feature; }
    public void setFeature(Feature feature) { this.feature = feature; }
    public Outcome getOutcome() { return outcome; }
    public void setOutcome(Outcome outcome) { this.outcome = outcome; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getModelBreakdownJson() { return modelBreakdownJson; }
    public void setModelBreakdownJson(String modelBreakdownJson) { this.modelBreakdownJson = modelBreakdownJson; }
    public int getLlmCalls() { return llmCalls; }
    public void setLlmCalls(int llmCalls) { this.llmCalls = llmCalls; }
    public int getFailedCalls() { return failedCalls; }
    public void setFailedCalls(int failedCalls) { this.failedCalls = failedCalls; }
    public int getRetries() { return retries; }
    public void setRetries(int retries) { this.retries = retries; }
    public long getPromptTokens() { return promptTokens; }
    public void setPromptTokens(long promptTokens) { this.promptTokens = promptTokens; }
    public long getCachedPromptTokens() { return cachedPromptTokens; }
    public void setCachedPromptTokens(long cachedPromptTokens) { this.cachedPromptTokens = cachedPromptTokens; }
    public long getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(long completionTokens) { this.completionTokens = completionTokens; }
    public long getReasoningTokens() { return reasoningTokens; }
    public void setReasoningTokens(long reasoningTokens) { this.reasoningTokens = reasoningTokens; }
    public BigDecimal getCostUsd() { return costUsd; }
    public void setCostUsd(BigDecimal costUsd) { this.costUsd = costUsd; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    public String getErrorKind() { return errorKind; }
    public void setErrorKind(String errorKind) { this.errorKind = errorKind; }
}
