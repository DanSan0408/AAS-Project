package com.capstone.adproject.service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;

import com.capstone.adproject.config.ErrorAlertAppender;
import com.capstone.adproject.config.ErrorAlertAppender.ErrorReport;
import com.capstone.adproject.service.MonitoringService.Check;
import com.capstone.adproject.service.MonitoringService.Level;
import com.sun.net.httpserver.HttpServer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Every error reaches Telegram, once per kind at a time, without secrets, and without being able to flood the chat. */
class ErrorAlertingTest {

    private HttpServer telegram;
    private final List<String> messages = new CopyOnWriteArrayList<>();
    private AlertingService alerting;
    private Instant now = Instant.parse("2026-11-21T10:00:00Z");

    @BeforeEach
    void setUp() throws IOException {
        telegram = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        telegram.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            for (String pair : body.split("&")) {
                if (pair.startsWith("text=")) messages.add(URLDecoder.decode(pair.substring(5), StandardCharsets.UTF_8));
            }
            byte[] ok = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, ok.length);
            exchange.getResponseBody().write(ok);
            exchange.close();
        });
        telegram.start();

        alerting = new AlertingService(new SimpleMeterRegistry(), mock(EmailService.class), mock(MonitoringService.class));
        ReflectionTestUtils.setField(alerting, "alertsEnabled", true);
        ReflectionTestUtils.setField(alerting, "telegramBotToken", "123456:SECRET-TOKEN");
        ReflectionTestUtils.setField(alerting, "telegramChatId", "-100999");
        ReflectionTestUtils.setField(alerting, "telegramApiBase", "http://127.0.0.1:" + telegram.getAddress().getPort());
        ReflectionTestUtils.setField(alerting, "environmentLabel", "production");
        alerting.dispatcher = Runnable::run;
        alerting.clock = new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now; }
        };
    }

    @AfterEach
    void tearDown() {
        ErrorAlertAppender.setSink(null);
        telegram.stop(0);
    }

    private static ErrorReport report(String logger, String exception, String message) {
        return new ErrorReport(Instant.now(), logger, "Request failed", exception, message, null,
                "com.capstone.adproject.service.Thing.run(Thing.java:42)", "abcdef12-3456", "admin1");
    }

    @Test
    void anErrorIsSentWithWhatHappenedWhereAndTheReferenceShownToTheUser() {
        alerting.reportError(report("com.capstone.adproject.controller.X", "java.lang.IllegalStateException", "boom"));

        assertEquals(1, messages.size());
        String text = messages.get(0);
        assertTrue(text.startsWith("[production] [ERROR] X"), text);
        assertTrue(text.contains("IllegalStateException: boom"));
        assertTrue(text.contains("at com.capstone.adproject.service.Thing.run(Thing.java:42)"));
        assertTrue(text.contains("Reference: ABCDEF12"));
    }

    @Test
    void theSameErrorAgainIsCountedNotResentAndTheCountGoesOutWithTheNextAlert() {
        ErrorReport r = report("a.B", "java.lang.RuntimeException", "db down 17");
        alerting.reportError(r);
        alerting.reportError(report("a.B", "java.lang.RuntimeException", "db down 99"));   // same kind: digits differ only
        alerting.reportError(r);
        assertEquals(1, messages.size());

        now = now.plusSeconds(6 * 60);
        alerting.reportError(r);

        assertEquals(2, messages.size());
        assertTrue(messages.get(1).contains("+2 more like this"), messages.get(1));
    }

    @Test
    void repeatsThatNeverGetAFollowUpAreStillReportedByTheMinuteSweep() {
        ErrorReport r = report("a.B", "java.lang.RuntimeException", "x");
        alerting.reportError(r);
        alerting.reportError(r);
        alerting.reportError(r);
        now = now.plusSeconds(6 * 60);

        alerting.flushCountedErrors();

        assertEquals(2, messages.size());
        assertTrue(messages.get(1).contains("2 more of: B: java.lang.RuntimeException"), messages.get(1));
        alerting.flushCountedErrors();
        assertEquals(2, messages.size(), "nothing is reported twice");
    }

    @Test
    void differentErrorsAreAllSent() {
        alerting.reportError(report("a.B", "java.lang.RuntimeException", "one"));
        alerting.reportError(report("a.C", "java.io.IOException", "two"));

        assertEquals(2, messages.size());
    }

    @Test
    void aStormOfDifferentErrorsIsCappedAndTheNextMessageSaysHowManyWereHeldBack() {
        for (int i = 0; i < 25; i++) {
            alerting.reportError(report("a.B" + i, "java.lang.RuntimeException", "kind " + i));
        }
        assertEquals(AlertingService.MAX_ERROR_MESSAGES_PER_WINDOW, messages.size());

        now = now.plusSeconds(11 * 60);
        alerting.reportError(report("a.Later", "java.lang.RuntimeException", "later"));

        assertEquals(AlertingService.MAX_ERROR_MESSAGES_PER_WINDOW + 1, messages.size());
        assertTrue(messages.get(messages.size() - 1).contains("held back by the flood limit"));
    }

    @Test
    void emailsCredentialsAndLongMessagesNeverReachTheChat() {
        String redacted = AlertingService.redact("Duplicate entry 'ali@utm.my' for key; password=hunter2 token: abc123 Bearer xyz.yyy", 300);

        assertFalse(redacted.contains("ali@utm.my"));
        assertFalse(redacted.contains("hunter2"));
        assertFalse(redacted.contains("abc123"));
        assertFalse(redacted.contains("xyz.yyy"));
        assertTrue(redacted.contains("[email]"));
        assertEquals(103, AlertingService.redact("x".repeat(500), 100).length());
    }

    @Test
    void nothingIsSentWhenAlertsAreOffOrTelegramIsNotConfigured() {
        ReflectionTestUtils.setField(alerting, "alertsEnabled", false);
        alerting.reportError(report("a.B", "java.lang.RuntimeException", "x"));
        ReflectionTestUtils.setField(alerting, "alertsEnabled", true);
        ReflectionTestUtils.setField(alerting, "telegramBotToken", "");
        alerting.reportError(report("a.B", "java.lang.RuntimeException", "x"));

        assertTrue(messages.isEmpty());
    }

    @Test
    void anUnreachableAiServiceIsAnnouncedAsDownNotAsAWarning() {
        alerting.evaluateHealth(List.of(new Check("AI service", Level.UP, "Healthy", 5L)));
        alerting.evaluateHealth(List.of(new Check("AI service", Level.WARN, "Not reachable.", null)));

        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("[DOWN] AI service"), messages.get(0));
    }

    // --- the log appender --------------------------------------------------------------------------------------------

    private Logger attachedLogger(List<ErrorReport> sink) {
        ErrorAlertAppender appender = new ErrorAlertAppender();
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        appender.setContext(context);
        appender.start();
        ErrorAlertAppender.setSink(sink::add);
        Logger logger = context.getLogger("com.capstone.adproject.test.AppenderCase");
        logger.setAdditive(false);
        logger.detachAndStopAllAppenders();
        logger.addAppender(appender);
        return logger;
    }

    @Test
    void everyErrorLogLineIsHandedOnWithItsCauseTheFirstAppFrameAndTheRequestReference() {
        List<ErrorReport> seen = new CopyOnWriteArrayList<>();
        Logger logger = attachedLogger(seen);
        MDC.put("requestId", "1234abcd-0000");
        try {
            logger.error("Could not save", new IllegalStateException("outer", new java.sql.SQLException("Connection refused")));
        } finally {
            MDC.clear();
        }

        assertEquals(1, seen.size());
        ErrorReport r = seen.get(0);
        assertEquals("Could not save", r.message());
        assertEquals("java.lang.IllegalStateException", r.exceptionClass());
        assertTrue(r.rootCause().contains("Connection refused"));
        assertEquals("1234abcd-0000", r.requestId());
        assertTrue(r.where() == null || r.where().startsWith("com.capstone.adproject."));
    }

    @Test
    void warningsAndBelowAreNotAlerts() {
        List<ErrorReport> seen = new CopyOnWriteArrayList<>();
        Logger logger = attachedLogger(seen);

        logger.warn("just a warning");
        logger.info("info");

        assertTrue(seen.isEmpty());
    }

    @Test
    void aVisitorClosingThePageIsNotAnError() {
        List<ErrorReport> seen = new CopyOnWriteArrayList<>();
        Logger logger = attachedLogger(seen);

        logger.error("Write failed", new java.io.IOException("Broken pipe"));

        assertTrue(seen.isEmpty());
    }

    @Test
    void aFailureInsideTheSinkNeverBreaksTheCodeThatLogged() {
        Logger logger = attachedLogger(new CopyOnWriteArrayList<>());
        ErrorAlertAppender.setSink(r -> { throw new IllegalStateException("sink broke"); });

        logger.error("still fine");   // must not throw
    }
}
