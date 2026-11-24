package com.capstone.adproject.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.capstone.adproject.dto.ai.AiCommentDtos;
import com.capstone.adproject.dto.ai.AiModerationDtos;
import com.capstone.adproject.dto.ai.AiTeamDynamicsDtos;
import com.capstone.adproject.model.AiUsageEvent.Feature;
import com.capstone.adproject.model.AiUsageEvent.Outcome;

@Service
public class AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);

    /** An identical request within this time is answered from memory, not by another paid model call. */
    static final Duration CACHE_TTL = Duration.ofMinutes(10);
    static final int CACHE_MAX_ENTRIES = 200;
    /** Calls that actually reach the model, per person: more than this in the window are refused until it passes. */
    static final int MAX_CALLS_PER_WINDOW = 12;
    static final Duration RATE_WINDOW = Duration.ofMinutes(10);

    /** The person has asked for too many AI analyses in a short time. */
    public static class RateLimitedException extends RuntimeException {
        public RateLimitedException() {
            super("Too many AI analyses requested in a short time.");
        }
    }

    private record Cached(Object value, Instant storedAt) {}

    private final RestClient restClient;
    private final String internalSecret;
    private final String baseUrl;
    private final Map<Object, Cached> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Object, Cached> eldest) {
            return size() > CACHE_MAX_ENTRIES;
        }
    };
    private final Map<String, Deque<Instant>> recentCalls = new HashMap<>();
    Clock clock = Clock.systemUTC();

    /** Records what each request used. Optional, so a client built without it (in tests) simply records nothing. */
    private AiUsageService usage;

    /** What is left on the provider account, as the AI service reports it. Amounts are in the account's own currency. */
    public record Balance(String provider, boolean supported, Boolean available, List<Amount> balances, String error) {}

    public record Amount(String currency, Double total, Double granted, Double toppedUp) {}

    static final Duration BALANCE_TTL = Duration.ofMinutes(5);
    private Balance cachedBalance;
    private Instant balanceFetchedAt;

    // Two constructors exist (this one and the test one below), so Spring must be told which to use; without this the
    // application fails to start with "No default constructor found".
    @Autowired
    public AiServiceClient(
            @Value("${app.ai-service.url:http://localhost:8000}") String baseUrl,
            @Value("${app.ai-service.secret:}") String internalSecret) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.internalSecret = internalSecret;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(60));

        this.restClient = RestClient.builder()
                .baseUrl(this.baseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Internal-Secret", internalSecret != null ? internalSecret : "")
                .build();
    }

    /** For tests: talk to a prepared client (for example a mock server) instead of building one. */
    AiServiceClient(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
        this.internalSecret = "";
    }

    @Autowired(required = false)
    void setUsage(AiUsageService usage) {
        this.usage = usage;
    }

    private static String caller() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getName() != null ? auth.getName() : "anonymous";
    }

    /** Saves one line of usage. Never throws: the figures must not be able to break an AI feature. */
    private void remember(Feature feature, Outcome outcome, AiUsageService.UsageHeader header, long startedNanos, String errorKind) {
        if (usage == null || outcome == null) return;
        try {
            usage.record(feature, outcome, caller(), header, (System.nanoTime() - startedNanos) / 1_000_000, errorKind);
        } catch (Exception e) {
            log.warn("AI usage could not be recorded ({})", e.getClass().getSimpleName());
        }
    }

    /**
     * Answers a repeat of a request from memory, refuses a person who asks too often, otherwise makes the call. Whichever
     * happens is recorded, so the usage page also shows what the cache and the limit saved.
     */
    @SuppressWarnings("unchecked")
    private <T> T cachedOrCall(Feature feature, Object key, String uri, Object body, Class<T> type, Function<T, String> statusOf) {
        Instant now = clock.instant();
        long started = System.nanoTime();
        Outcome early = null;
        T kept = null;
        synchronized (this) {
            Cached hit = cache.get(key);
            if (hit != null && Duration.between(hit.storedAt(), now).compareTo(CACHE_TTL) < 0) {
                kept = (T) hit.value();
                early = Outcome.CACHE_HIT;
            } else {
                Deque<Instant> calls = recentCalls.computeIfAbsent(caller(), k -> new ArrayDeque<>());
                while (!calls.isEmpty() && Duration.between(calls.peekFirst(), now).compareTo(RATE_WINDOW) >= 0) {
                    calls.pollFirst();
                }
                if (calls.size() >= MAX_CALLS_PER_WINDOW) {
                    early = Outcome.RATE_LIMITED;
                } else {
                    calls.addLast(now);
                }
            }
        }
        if (early != null) {
            remember(feature, early, null, started, null);   // outside the lock: this writes to the database
            if (early == Outcome.RATE_LIMITED) throw new RateLimitedException();
            return kept;
        }
        ResponseEntity<T> entity;
        try {
            entity = restClient.post().uri(uri).body(body).retrieve().toEntity(type);
        } catch (RestClientException e) {
            remember(feature, Outcome.SERVICE_ERROR, null, started, describe(e));
            throw e;
        }
        T answer = entity.getBody();
        String aiStatus = answer == null ? null : statusOf.apply(answer);
        AiUsageService.UsageHeader header = AiUsageService.parseHeader(entity.getHeaders().getFirst(AiUsageService.USAGE_HEADER));
        Outcome outcome = AiUsageService.outcomeOf(aiStatus);
        // "The AI was not needed" and no model call: nothing to record. A model call is always recorded.
        if (outcome != null || header != null) {
            remember(feature, outcome == null ? Outcome.OK : outcome, header, started, null);
        }
        // A degraded answer (the AI was down) is not kept, so the next click can try again.
        if (answer != null && aiRan(aiStatus)) {
            synchronized (this) {
                cache.put(key, new Cached(answer, now));
            }
        }
        return answer;
    }

    private static boolean aiRan(String status) {
        return status == null || "OK".equals(status) || "NOT_USED".equals(status);
    }

    public boolean isServiceAvailable() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(Map.class);
            return resp != null && "healthy".equals(resp.get("status"));
        } catch (Exception e) {
            log.debug("AI service health check failed at {}: {}", baseUrl, e.getMessage());
            return false;
        }
    }

    public AiTeamDynamicsDtos.AuditResponse auditTeamDynamics(AiTeamDynamicsDtos.AuditRequest request) {
        try {
            return cachedOrCall(Feature.TEAM_AUDIT, Map.entry("team-audit", request), "/api/v1/team-dynamics/audit", request,
                    AiTeamDynamicsDtos.AuditResponse.class, AiTeamDynamicsDtos.AuditResponse::aiStatus);
        } catch (RestClientException e) {
            log.error("Failed to call AI service for team dynamics audit: {}", describe(e));
            throw new RuntimeException("AI service is currently unavailable or returned an error: " + describe(e), e);
        }
    }

    public AiModerationDtos.BriefingResponse generateModerationBriefing(AiModerationDtos.BriefingRequest request) {
        try {
            return cachedOrCall(Feature.MODERATION_BRIEFING, Map.entry("moderation-briefing", request), "/api/v1/moderation/briefing", request,
                    AiModerationDtos.BriefingResponse.class, AiModerationDtos.BriefingResponse::aiStatus);
        } catch (RestClientException e) {
            log.error("Failed to call AI service for moderation briefing: {}", describe(e));
            throw new RuntimeException("AI service is currently unavailable or returned an error: " + describe(e), e);
        }
    }

    /** Asks the AI service to read a batch of comments (at most 20) and say which deserve a moderator's look. */
    public AiCommentDtos.CheckResponse checkComments(AiCommentDtos.CheckRequest request) {
        try {
            return cachedOrCall(Feature.COMMENT_CHECK, Map.entry("comment-check", request), "/api/v1/comments/check", request,
                    AiCommentDtos.CheckResponse.class, AiCommentDtos.CheckResponse::aiStatus);
        } catch (RestClientException e) {
            log.error("Failed to call AI service for the comment check: {}", describe(e));
            throw new RuntimeException("AI service is currently unavailable or returned an error.", e);
        }
    }

    /**
     * What is left on the provider account, for the AI usage page. Kept for five minutes so reloading the page does not call the
     * provider each time. Never throws: when the AI service cannot say, the answer carries a reason and is not kept.
     */
    public Balance providerBalance() {
        Instant now = clock.instant();
        synchronized (this) {
            if (cachedBalance != null && balanceFetchedAt != null && Duration.between(balanceFetchedAt, now).compareTo(BALANCE_TTL) < 0) {
                return cachedBalance;
            }
        }
        try {
            Balance fresh = restClient.get().uri("/api/v1/usage/balance").retrieve().body(Balance.class);
            if (fresh == null) throw new IllegalStateException("empty");
            if (fresh.error() == null) {
                synchronized (this) {
                    cachedBalance = fresh;
                    balanceFetchedAt = now;
                }
            }
            return fresh;
        } catch (Exception e) {
            return new Balance(null, true, null, List.of(), "The AI service could not be reached.");
        }
    }

    /**
     * What went wrong, without the response body: when the AI service refuses a request it echoes the offending input back, and
     * that input is a student's comment. The status (or the kind of failure) is all anyone needs in a log line or an alert.
     */
    static String describe(RestClientException e) {
        if (e instanceof org.springframework.web.client.RestClientResponseException response) {
            return "HTTP " + response.getStatusCode().value();
        }
        return e.getClass().getSimpleName();
    }
}
