package com.capstone.adproject.service;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.capstone.adproject.dto.ai.AiModerationDtos;
import com.capstone.adproject.dto.ai.AiTeamDynamicsDtos;

@Service
public class AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);

    private final RestClient restClient;
    private final String internalSecret;
    private final String baseUrl;

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
            return restClient.post()
                    .uri("/api/v1/team-dynamics/audit")
                    .body(request)
                    .retrieve()
                    .body(AiTeamDynamicsDtos.AuditResponse.class);
        } catch (RestClientException e) {
            log.error("Failed to call AI service for team dynamics audit: {}", e.getMessage());
            throw new RuntimeException("AI service is currently unavailable or returned an error: " + e.getMessage(), e);
        }
    }

    public AiModerationDtos.BriefingResponse generateModerationBriefing(AiModerationDtos.BriefingRequest request) {
        try {
            return restClient.post()
                    .uri("/api/v1/moderation/briefing")
                    .body(request)
                    .retrieve()
                    .body(AiModerationDtos.BriefingResponse.class);
        } catch (RestClientException e) {
            log.error("Failed to call AI service for moderation briefing: {}", e.getMessage());
            throw new RuntimeException("AI service is currently unavailable or returned an error: " + e.getMessage(), e);
        }
    }
}

