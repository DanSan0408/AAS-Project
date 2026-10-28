package com.capstone.adproject.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.capstone.adproject.dto.ai.AiModerationDtos;
import com.capstone.adproject.dto.ai.AiTeamDynamicsDtos;

class AiServiceClientTest {

    private AiServiceClient aiServiceClient;

    @BeforeEach
    void setUp() {
        // Point to an unreachable port to test resilience & exception handling
        aiServiceClient = new AiServiceClient("http://127.0.0.1:54321", "test-secret");
    }

    @Test
    void isServiceAvailableReturnsFalseWhenUnreachable() {
        assertFalse(aiServiceClient.isServiceAvailable());
    }

    @Test
    void auditTeamDynamicsThrowsWhenServiceOffline() {
        AiTeamDynamicsDtos.AuditRequest req = new AiTeamDynamicsDtos.AuditRequest(
                1L, "Team Alpha", 10L, "Capstone Assessment", List.of(), List.of(), List.of()
        );
        assertThrows(RuntimeException.class, () -> aiServiceClient.auditTeamDynamics(req));
    }

    @Test
    void generateModerationBriefingThrowsWhenServiceOffline() {
        AiModerationDtos.BriefingRequest req = new AiModerationDtos.BriefingRequest(
                1L, "Title", List.of(), List.of(), List.of()
        );
        assertThrows(RuntimeException.class, () -> aiServiceClient.generateModerationBriefing(req));
    }
}

