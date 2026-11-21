package com.capstone.adproject.service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.capstone.adproject.service.MonitoringService.Check;
import com.capstone.adproject.service.MonitoringService.Level;
import com.sun.net.httpserver.HttpServer;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AlertingServiceTest {

    private HttpServer telegram;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger telegramStatus = new AtomicInteger(200);
    private EmailService emailService;
    private AlertingService alerting;

    @BeforeEach
    void setUp() throws IOException {
        telegram = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        telegram.createContext("/", exchange -> {
            requests.add(exchange.getRequestURI().getPath() + "?" + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(telegramStatus.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        telegram.start();

        emailService = mock(EmailService.class);
        alerting = new AlertingService(new SimpleMeterRegistry(), emailService, mock(MonitoringService.class));
        ReflectionTestUtils.setField(alerting, "alertsEnabled", true);
        ReflectionTestUtils.setField(alerting, "recipientEmail", "ops@example.com");
        ReflectionTestUtils.setField(alerting, "telegramBotToken", "123456:SECRET-TOKEN");
        ReflectionTestUtils.setField(alerting, "telegramChatId", "-100999");
        ReflectionTestUtils.setField(alerting, "telegramApiBase", "http://127.0.0.1:" + telegram.getAddress().getPort());
        ReflectionTestUtils.setField(alerting, "environmentLabel", "production");
    }

    @AfterEach
    void tearDown() {
        telegram.stop(0);
    }

    private static String decoded(String raw) {
        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
    }

    @Test
    void aDependencyGoingDownIsAnnouncedOnceOnBothChannelsAndAgainWhenItRecovers() {
        alerting.evaluateHealth(List.of(new Check("Database", Level.UP, "Responding", 5L)));
        assertTrue(requests.isEmpty(), "a healthy first observation is not news");

        alerting.evaluateHealth(List.of(new Check("Database", Level.DOWN, "Query failed", null)));
        alerting.evaluateHealth(List.of(new Check("Database", Level.DOWN, "Query failed", null)));

        assertEquals(1, requests.size(), "staying down must not repeat the alert every minute");
        assertTrue(requests.get(0).startsWith("/bot123456:SECRET-TOKEN/sendMessage?"));
        String sent = decoded(requests.get(0));
        assertTrue(sent.contains("chat_id=-100999"));
        assertTrue(sent.contains("[DOWN] Database"));
        assertTrue(sent.contains("[production]"));
        verify(emailService, times(1)).sendAlertEmail(eq("ops@example.com"), eq("[DOWN] Database"), anyString());

        alerting.evaluateHealth(List.of(new Check("Database", Level.UP, "Responding", 5L)));

        assertEquals(2, requests.size());
        assertTrue(decoded(requests.get(1)).contains("[RECOVERED] Database"));
    }

    @Test
    void aDegradedServiceOnFirstObservationIsReported() {
        alerting.evaluateHealth(List.of(new Check("Email", Level.WARN, "SPRING_MAIL_FROM missing", null)));

        assertEquals(1, requests.size());
        assertTrue(decoded(requests.get(0)).contains("[WARNING] Email"));
    }

    @Test
    void telegramRejectionIsReportedAsAFailureAndNeverLeaksTheTokenIntoTheSummary() {
        telegramStatus.set(400);

        String summary = alerting.notifyAll("[TEST] subject", "body");

        assertTrue(summary.contains("telegram failed"));
        assertFalse(summary.contains("SECRET-TOKEN"));
    }

    @Test
    void unconfiguredChannelsAreSkippedWithoutAnyNetworkCall() {
        ReflectionTestUtils.setField(alerting, "telegramBotToken", "");
        ReflectionTestUtils.setField(alerting, "recipientEmail", "admin@example.com");

        String summary = alerting.notifyAll("subject", "body");

        assertEquals("email not configured; telegram not configured", summary);
        assertTrue(requests.isEmpty());
        verify(emailService, never()).sendAlertEmail(anyString(), anyString(), anyString());
    }
}
