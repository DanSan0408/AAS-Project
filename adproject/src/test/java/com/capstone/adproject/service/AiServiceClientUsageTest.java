package com.capstone.adproject.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.capstone.adproject.dto.ai.AiCommentDtos;
import com.capstone.adproject.dto.ai.AiTeamDynamicsDtos;
import com.capstone.adproject.model.AiUsageEvent.Feature;
import com.capstone.adproject.model.AiUsageEvent.Outcome;

/** Every AI request is recorded: what it used, how it ended, and what the cache and the limit saved. Recording can never break it. */
class AiServiceClientUsageTest {

    private static final String AUDIT_URL = "http://ai.test/api/v1/team-dynamics/audit";
    private static final String AUDIT_OK = "{\"groupId\":1,\"groupName\":\"T\",\"teamHealthScore\":90,\"collaborationStatus\":\"HEALTHY_COLLABORATION\","
            + "\"aiStatus\":\"OK\",\"aiNotes\":[],\"executiveSummary\":\"s\",\"memberMetrics\":[],\"anomalyFlags\":[],\"recommendedActions\":[]}";
    private static final String HEADER = "{\"provider\":\"deepseek\",\"model\":\"deepseek-flash\",\"calls\":1,\"failedCalls\":0,\"retries\":0,"
            + "\"promptTokens\":1000,\"cachedPromptTokens\":400,\"completionTokens\":200,\"reasoningTokens\":0,\"modelMs\":1500,\"costUsd\":0.000212,\"errors\":[]}";
    private static final String COMMENTS_NOT_USED = "{\"results\":[],\"aiStatus\":\"NOT_USED\",\"aiNotes\":[]}";

    private MockRestServiceServer server;
    private AiServiceClient client;
    private AiUsageService usage;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AiServiceClient(builder.build(), "http://ai.test");
        usage = mock(AiUsageService.class);
        client.setUsage(usage);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin1", "n/a", List.of()));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private static AiTeamDynamicsDtos.AuditRequest audit(long groupId) {
        return new AiTeamDynamicsDtos.AuditRequest(groupId, "Team", 10L, "Assessment", List.of(), List.of(), List.of());
    }

    private void answerAudit(int times, String body, String usageHeader) {
        var response = withSuccess(body, MediaType.APPLICATION_JSON);
        if (usageHeader != null) response.header(AiUsageService.USAGE_HEADER, usageHeader);
        server.expect(ExpectedCount.times(times), requestTo(AUDIT_URL)).andRespond(response);
    }

    @Test
    void aRequestThatUsedTheModelIsRecordedWithItsTokensCostWhoAskedAndHowLongItTook() {
        answerAudit(1, AUDIT_OK, HEADER);
        client.auditTeamDynamics(audit(1));

        var header = ArgumentCaptor.forClass(AiUsageService.UsageHeader.class);
        verify(usage).record(eq(Feature.TEAM_AUDIT), eq(Outcome.OK), eq("admin1"), header.capture(), anyLong(), eq(null));
        assertEquals(1000, header.getValue().promptTokens());
        assertEquals(400, header.getValue().cachedPromptTokens());
        assertEquals("deepseek-flash", header.getValue().model());
    }

    @Test
    void aSecondIdenticalRequestIsRecordedAsSavedNotAsAnotherCost() {
        answerAudit(1, AUDIT_OK, HEADER);
        client.auditTeamDynamics(audit(1));
        client.auditTeamDynamics(audit(1));

        verify(usage).record(eq(Feature.TEAM_AUDIT), eq(Outcome.OK), any(), any(), anyLong(), any());
        verify(usage).record(eq(Feature.TEAM_AUDIT), eq(Outcome.CACHE_HIT), eq("admin1"), eq(null), anyLong(), eq(null));
        server.verify();
    }

    @Test
    void aPersonOverTheLimitIsRefusedAndTheRefusalIsRecorded() {
        answerAudit(AiServiceClient.MAX_CALLS_PER_WINDOW, AUDIT_OK, HEADER);
        for (int i = 0; i < AiServiceClient.MAX_CALLS_PER_WINDOW; i++) client.auditTeamDynamics(audit(i));

        assertThrows(AiServiceClient.RateLimitedException.class, () -> client.auditTeamDynamics(audit(999)));

        verify(usage).record(eq(Feature.TEAM_AUDIT), eq(Outcome.RATE_LIMITED), eq("admin1"), eq(null), anyLong(), eq(null));
        verify(usage, times(AiServiceClient.MAX_CALLS_PER_WINDOW)).record(any(), eq(Outcome.OK), any(), any(), anyLong(), any());
    }

    @Test
    void aModelThatWasDownIsRecordedAsUnavailableWithItsFailureKind() {
        String down = AUDIT_OK.replace("\"aiStatus\":\"OK\"", "\"aiStatus\":\"UNAVAILABLE\"");
        String failed = HEADER.replace("\"failedCalls\":0", "\"failedCalls\":1").replace("\"errors\":[]", "\"errors\":[\"HTTP402\"]");
        answerAudit(1, down, failed);
        client.auditTeamDynamics(audit(1));

        var header = ArgumentCaptor.forClass(AiUsageService.UsageHeader.class);
        verify(usage).record(eq(Feature.TEAM_AUDIT), eq(Outcome.UNAVAILABLE), eq("admin1"), header.capture(), anyLong(), eq(null));
        assertEquals(List.of("HTTP402"), header.getValue().errors());
    }

    @Test
    void aFailureOfTheAiServiceItselfIsRecordedWithOnlyTheStatus() {
        server.expect(requestTo(AUDIT_URL)).andRespond(withServerError());

        assertThrows(RuntimeException.class, () -> client.auditTeamDynamics(audit(1)));

        verify(usage).record(eq(Feature.TEAM_AUDIT), eq(Outcome.SERVICE_ERROR), eq("admin1"), eq(null), anyLong(), eq("HTTP 500"));
    }

    @Test
    void aRequestThatNeededNoAiAndMadeNoModelCallIsNotRecorded() {
        server.expect(requestTo("http://ai.test/api/v1/comments/check"))
                .andRespond(withSuccess(COMMENTS_NOT_USED, MediaType.APPLICATION_JSON));

        client.checkComments(new AiCommentDtos.CheckRequest(List.of()));

        verifyNoInteractions(usage);
    }

    @Test
    void theScheduledScanIsAttributedToTheSystemNotToANobody() {
        SecurityContextHolder.clearContext();
        server.expect(requestTo("http://ai.test/api/v1/comments/check")).andRespond(withSuccess(
                "{\"results\":[],\"aiStatus\":\"OK\",\"aiNotes\":[]}", MediaType.APPLICATION_JSON).header(AiUsageService.USAGE_HEADER, HEADER));

        client.checkComments(new AiCommentDtos.CheckRequest(List.of()));

        verify(usage).record(eq(Feature.COMMENT_CHECK), eq(Outcome.OK), eq("anonymous"), any(), anyLong(), any());
    }

    @Test
    void aMissingOrBrokenUsageHeaderStillRecordsTheRequestWithoutNumbers() {
        answerAudit(1, AUDIT_OK, "garbage{");
        client.auditTeamDynamics(audit(1));

        verify(usage).record(eq(Feature.TEAM_AUDIT), eq(Outcome.OK), eq("admin1"), eq(null), anyLong(), eq(null));
    }

    @Test
    void aFailureToRecordNeverBreaksTheAiFeature() {
        doThrow(new IllegalStateException("database is down")).when(usage).record(any(), any(), any(), any(), anyLong(), any());
        answerAudit(1, AUDIT_OK, HEADER);

        assertNotNull(client.auditTeamDynamics(audit(1)));
    }

    @Test
    void aClientBuiltWithoutARecorderSimplyRecordsNothing() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
        MockRestServiceServer plain = MockRestServiceServer.bindTo(builder).build();
        AiServiceClient bare = new AiServiceClient(builder.build(), "http://ai.test");
        plain.expect(requestTo(AUDIT_URL)).andRespond(withSuccess(AUDIT_OK, MediaType.APPLICATION_JSON).header(AiUsageService.USAGE_HEADER, HEADER));

        assertNotNull(bare.auditTeamDynamics(audit(1)));
        verify(usage, never()).record(any(), any(), any(), any(), anyLong(), any());
    }

    // ---- The provider balance ----------------------------------------------------------------------------------------

    private static final String BALANCE_URL = "http://ai.test/api/v1/usage/balance";
    private static final String BALANCE_JSON = "{\"provider\":\"deepseek\",\"supported\":true,\"available\":true,"
            + "\"balances\":[{\"currency\":\"USD\",\"total\":12.5,\"granted\":2.5,\"toppedUp\":10.0}],\"error\":null}";

    @Test
    void theBalanceIsReadAndKeptForFiveMinutes() {
        Instant start = Instant.parse("2026-11-23T10:00:00Z");
        client.clock = Clock.fixed(start, ZoneOffset.UTC);
        server.expect(ExpectedCount.times(2), requestTo(BALANCE_URL)).andRespond(withSuccess(BALANCE_JSON, MediaType.APPLICATION_JSON));

        var first = client.providerBalance();
        client.clock = Clock.fixed(start.plusSeconds(299), ZoneOffset.UTC);
        client.providerBalance();                                          // still from memory
        client.clock = Clock.fixed(start.plusSeconds(301), ZoneOffset.UTC);
        client.providerBalance();                                          // asked again

        assertEquals("deepseek", first.provider());
        assertEquals(12.5, first.balances().get(0).total());
        assertEquals("USD", first.balances().get(0).currency());
        server.verify();
    }

    @Test
    void aBalanceThatCouldNotBeReadIsNotKeptSoTheNextLoadTriesAgain() {
        server.expect(ExpectedCount.times(2), requestTo(BALANCE_URL)).andRespond(withSuccess(
                "{\"provider\":\"deepseek\",\"supported\":true,\"available\":null,\"balances\":[],\"error\":\"HTTP 401\"}", MediaType.APPLICATION_JSON));

        assertEquals("HTTP 401", client.providerBalance().error());
        assertEquals("HTTP 401", client.providerBalance().error());
        server.verify();
    }

    @Test
    void anUnreachableAiServiceGivesAReasonNotAnException() {
        server.expect(requestTo(BALANCE_URL)).andRespond(withServerError());

        var balance = client.providerBalance();

        assertNotNull(balance.error());
        assertTrue(balance.balances().isEmpty());
        assertNull(balance.available());
    }

    @Test
    void anUnsupportedProviderIsReportedAsSuch() {
        server.expect(requestTo(BALANCE_URL)).andRespond(withSuccess(
                "{\"provider\":\"gemini\",\"supported\":false,\"available\":null,\"balances\":[],\"error\":null}", MediaType.APPLICATION_JSON));

        var balance = client.providerBalance();

        assertEquals(false, balance.supported());
        assertEquals("gemini", balance.provider());
    }
}
