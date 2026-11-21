package com.capstone.adproject.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.capstone.adproject.dto.ai.AiCommentDtos;
import com.capstone.adproject.dto.ai.AiModerationDtos;
import com.capstone.adproject.dto.ai.AiTeamDynamicsDtos;

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

    private static String caller() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getName() != null ? auth.getName() : "anonymous";
    }

    /** Answers a repeat of a request from memory, refuses a person who asks too often, otherwise makes the call. */
    @SuppressWarnings("unchecked")
    private <T> T cachedOrCall(Object key, Supplier<T> call, Predicate<T> worthKeeping) {
        Instant now = clock.instant();
        synchronized (this) {
            Cached hit = cache.get(key);
            if (hit != null && Duration.between(hit.storedAt(), now).compareTo(CACHE_TTL) < 0) {
                return (T) hit.value();
            }
            Deque<Instant> calls = recentCalls.computeIfAbsent(caller(), k -> new ArrayDeque<>());
            while (!calls.isEmpty() && Duration.between(calls.peekFirst(), now).compareTo(RATE_WINDOW) >= 0) {
                calls.pollFirst();
            }
            if (calls.size() >= MAX_CALLS_PER_WINDOW) {
                throw new RateLimitedException();
            }
            calls.addLast(now);
        }
        T answer = call.get();
        // A degraded answer (the AI was down) is not kept, so the next click can try again.
        if (answer != null && worthKeeping.test(answer)) {
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
            return cachedOrCall(Map.entry("team-audit", request), () -> restClient.post()
                    .uri("/api/v1/team-dynamics/audit")
                    .body(request)
                    .retrieve()
                    .body(AiTeamDynamicsDtos.AuditResponse.class), r -> aiRan(r.aiStatus()));
        } catch (RestClientException e) {
            log.error("Failed to call AI service for team dynamics audit: {}", e.getMessage());
            throw new RuntimeException("AI service is currently unavailable or returned an error: " + e.getMessage(), e);
        }
    }

    public AiModerationDtos.BriefingResponse generateModerationBriefing(AiModerationDtos.BriefingRequest request) {
        try {
            return cachedOrCall(Map.entry("moderation-briefing", request), () -> restClient.post()
                    .uri("/api/v1/moderation/briefing")
                    .body(request)
                    .retrieve()
                    .body(AiModerationDtos.BriefingResponse.class), r -> aiRan(r.aiStatus()));
        } catch (RestClientException e) {
            log.error("Failed to call AI service for moderation briefing: {}", e.getMessage());
            throw new RuntimeException("AI service is currently unavailable or returned an error: " + e.getMessage(), e);
        }
    }

    /** Asks the AI service to read a batch of comments (at most 20) and say which deserve a moderator's look. */
    public AiCommentDtos.CheckResponse checkComments(AiCommentDtos.CheckRequest request) {
        try {
            return cachedOrCall(Map.entry("comment-check", request), () -> restClient.post()
                    .uri("/api/v1/comments/check")
                    .body(request)
                    .retrieve()
                    .body(AiCommentDtos.CheckResponse.class), r -> aiRan(r.aiStatus()));
        } catch (RestClientException e) {
            log.error("Failed to call AI service for the comment check: {}", e.getMessage());
            throw new RuntimeException("AI service is currently unavailable or returned an error.", e);
        }
    }
}
