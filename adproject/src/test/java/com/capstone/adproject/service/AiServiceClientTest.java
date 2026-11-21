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

    // --- Repeat requests are answered from memory, and nobody can run up the model bill ------------------------------

    private static final String AUDIT_OK = "{\"groupId\":1,\"groupName\":\"T\",\"teamHealthScore\":90,\"collaborationStatus\":\"HEALTHY_COLLABORATION\","
            + "\"aiStatus\":\"OK\",\"aiNotes\":[],\"executiveSummary\":\"s\",\"memberMetrics\":[],\"anomalyFlags\":[],\"recommendedActions\":[]}";
    private static final String AUDIT_DOWN = AUDIT_OK.replace("\"aiStatus\":\"OK\"", "\"aiStatus\":\"UNAVAILABLE\"");

    private org.springframework.test.web.client.MockRestServiceServer server;
    private AiServiceClient client;

    private void mocked() {
        org.springframework.web.client.RestClient.Builder builder = org.springframework.web.client.RestClient.builder().baseUrl("http://ai.test");
        server = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
        client = new AiServiceClient(builder.build(), "http://ai.test");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("admin1", "n/a", List.of()));
    }

    private static AiTeamDynamicsDtos.AuditRequest audit(long groupId) {
        return new AiTeamDynamicsDtos.AuditRequest(groupId, "Team", 10L, "Assessment", List.of(), List.of(), List.of());
    }

    private void respond(int times, String body) {
        server.expect(org.springframework.test.web.client.ExpectedCount.times(times),
                        org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://ai.test/api/v1/team-dynamics/audit"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(body, org.springframework.http.MediaType.APPLICATION_JSON));
    }

    @org.junit.jupiter.api.AfterEach
    void clearSecurity() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void anIdenticalRequestIsAnsweredFromMemoryNotByAnotherCall() {
        mocked();
        respond(1, AUDIT_OK);

        client.auditTeamDynamics(audit(1));
        client.auditTeamDynamics(audit(1));

        server.verify();   // exactly one request reached the service
    }

    @Test
    void aDifferentRequestIsCalledAfresh() {
        mocked();
        respond(2, AUDIT_OK);

        client.auditTeamDynamics(audit(1));
        client.auditTeamDynamics(audit(2));

        server.verify();
    }

    @Test
    void aDegradedAnswerIsNotKeptSoTheNextClickCanTryAgain() {
        mocked();
        respond(2, AUDIT_DOWN);

        client.auditTeamDynamics(audit(1));
        client.auditTeamDynamics(audit(1));

        server.verify();
    }

    @Test
    void anAnswerIsKeptOnlyForTenMinutes() {
        mocked();
        respond(2, AUDIT_OK);
        java.time.Instant start = java.time.Instant.parse("2026-11-21T10:00:00Z");
        client.clock = java.time.Clock.fixed(start, java.time.ZoneOffset.UTC);
        client.auditTeamDynamics(audit(1));

        client.clock = java.time.Clock.fixed(start.plusSeconds(9 * 60), java.time.ZoneOffset.UTC);
        client.auditTeamDynamics(audit(1));        // still from memory
        client.clock = java.time.Clock.fixed(start.plusSeconds(11 * 60), java.time.ZoneOffset.UTC);
        client.auditTeamDynamics(audit(1));        // expired: a second real call

        server.verify();
    }

    @Test
    void aPersonWhoAsksTooOftenIsHeldBackUntilTheWindowPasses() {
        mocked();
        respond(AiServiceClient.MAX_CALLS_PER_WINDOW + 1, AUDIT_OK);
        java.time.Instant start = java.time.Instant.parse("2026-11-21T10:00:00Z");
        client.clock = java.time.Clock.fixed(start, java.time.ZoneOffset.UTC);
        for (int i = 0; i < AiServiceClient.MAX_CALLS_PER_WINDOW; i++) {
            client.auditTeamDynamics(audit(100 + i));      // each one different, so each reaches the model
        }

        assertThrows(AiServiceClient.RateLimitedException.class, () -> client.auditTeamDynamics(audit(999)));

        client.clock = java.time.Clock.fixed(start.plusSeconds(11 * 60), java.time.ZoneOffset.UTC);
        client.auditTeamDynamics(audit(999));              // allowed again
        server.verify();
    }

    @Test
    void anotherPersonIsNotAffectedBySomeonesLimit() {
        mocked();
        respond(AiServiceClient.MAX_CALLS_PER_WINDOW + 1, AUDIT_OK);
        for (int i = 0; i < AiServiceClient.MAX_CALLS_PER_WINDOW; i++) {
            client.auditTeamDynamics(audit(100 + i));
        }
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("someone.else", "n/a", List.of()));

        client.auditTeamDynamics(audit(999));

        server.verify();
    }

    @Test
    void theCommentCheckIsPostedToItsEndpointAndTheVerdictsAreRead() {
        mocked();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://ai.test/api/v1/comments/check"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        "{\"results\":[{\"id\":\"4\",\"label\":\"abusive\",\"confidence\":0.9,\"language\":\"ms\",\"reason\":\"Insult\","
                                + "\"needsHumanReview\":true,\"flagged\":true,\"source\":\"AI+RULES\"}],\"aiStatus\":\"OK\",\"aiNotes\":[]}",
                        org.springframework.http.MediaType.APPLICATION_JSON));

        com.capstone.adproject.dto.ai.AiCommentDtos.CheckResponse response = client.checkComments(
                new com.capstone.adproject.dto.ai.AiCommentDtos.CheckRequest(List.of(
                        new com.capstone.adproject.dto.ai.AiCommentDtos.Item("4", "bodoh"))));

        server.verify();
        org.junit.jupiter.api.Assertions.assertEquals("abusive", response.results().get(0).label());
        org.junit.jupiter.api.Assertions.assertTrue(response.results().get(0).flagged());
    }
}
