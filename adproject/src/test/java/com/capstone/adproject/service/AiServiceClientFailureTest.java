package com.capstone.adproject.service;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.capstone.adproject.dto.ai.AiCommentDtos;
import com.capstone.adproject.dto.ai.AiTeamDynamicsDtos;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * One comment longer than the AI service accepts must not fail the whole batch (so the scan never gets stuck on it), and when
 * the AI service does refuse a request, the error shown and logged says what happened without repeating the student's words.
 */
class AiServiceClientFailureTest {

    private static final String PRIVATE_TEXT = "Aiman never did his part and is lazy";

    private MockRestServiceServer server;
    private AiServiceClient client;
    private ListAppender<ILoggingEvent> logs;
    private Logger clientLogger;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AiServiceClient(builder.build(), "http://ai.test");
        clientLogger = (Logger) LoggerFactory.getLogger(AiServiceClient.class);
        logs = new ListAppender<>();
        logs.start();
        clientLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        clientLogger.detachAppender(logs);
    }

    private String loggedText() {
        StringBuilder all = new StringBuilder();
        for (ILoggingEvent event : logs.list) {
            all.append(event.getFormattedMessage()).append('\n');
        }
        return all.toString();
    }

    private void refuseWithTheCommentsEchoedBack(String path) {
        server.expect(requestTo("http://ai.test" + path))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":[{\"type\":\"string_too_long\",\"input\":\"" + PRIVATE_TEXT + "\"}]}"));
    }

    // --- long comments ---------------------------------------------------------------------------------------------

    @Test
    void aCommentLongerThanTheAiServiceAcceptsIsCutToTheLimitBeforeItIsSent() {
        String huge = "x".repeat(AiCommentDtos.MAX_TEXT_CHARS + 1500);

        AiCommentDtos.Item item = new AiCommentDtos.Item("7", huge);

        assertEquals(AiCommentDtos.MAX_TEXT_CHARS, item.text().length());
        assertEquals("7", item.id());
    }

    @Test
    void aShortCommentAndANullCommentAreLeftAlone() {
        assertEquals("Good work", new AiCommentDtos.Item("1", "Good work").text());
        assertEquals("x".repeat(AiCommentDtos.MAX_TEXT_CHARS), new AiCommentDtos.Item("1", "x".repeat(AiCommentDtos.MAX_TEXT_CHARS)).text());
        assertEquals(null, new AiCommentDtos.Item("1", null).text());
    }

    @Test
    void cuttingNeverSplitsAnEmoji() {
        String text = "a".repeat(AiCommentDtos.MAX_TEXT_CHARS - 1) + "😀 and more"; // the pair straddles the limit

        String cut = new AiCommentDtos.Item("1", text).text();

        assertFalse(Character.isHighSurrogate(cut.charAt(cut.length() - 1)), "a half emoji would be invalid text");
        assertTrue(cut.length() <= AiCommentDtos.MAX_TEXT_CHARS);
    }

    // --- failures do not echo the student's words ---------------------------------------------------------------------

    @Test
    void aRefusedCommentCheckIsLoggedWithItsStatusAndNotTheCommentText() {
        refuseWithTheCommentsEchoedBack("/api/v1/comments/check");

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> client.checkComments(new AiCommentDtos.CheckRequest(List.of(new AiCommentDtos.Item("1", PRIVATE_TEXT)))));

        assertFalse(loggedText().contains(PRIVATE_TEXT), loggedText());
        assertTrue(loggedText().contains("422"), "the status is what helps: " + loggedText());
        assertFalse(String.valueOf(failure.getMessage()).contains(PRIVATE_TEXT));
    }

    @Test
    void aRefusedTeamAuditNeitherLogsNorThrowsTheCommentText() {
        refuseWithTheCommentsEchoedBack("/api/v1/team-dynamics/audit");

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> client.auditTeamDynamics(new AiTeamDynamicsDtos.AuditRequest(1L, "Team", 10L, "Assessment", List.of(), List.of(), List.of())));

        assertFalse(loggedText().contains(PRIVATE_TEXT), loggedText());
        assertFalse(String.valueOf(failure.getMessage()).contains(PRIVATE_TEXT), failure.getMessage());
        assertTrue(failure.getMessage().contains("422"), "the person is told what went wrong: " + failure.getMessage());
    }
}
