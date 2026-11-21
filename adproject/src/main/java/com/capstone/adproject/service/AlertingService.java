package com.capstone.adproject.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.capstone.adproject.config.ErrorAlertAppender;
import com.capstone.adproject.config.ErrorAlertAppender.ErrorReport;
import com.capstone.adproject.service.MonitoringService.Check;
import com.capstone.adproject.service.MonitoringService.Level;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;

/**
 * Watches application metrics and dependency health and pushes an alert when something needs attention.
 * Alerts go to the configured recipient email and, when a bot token and chat id are set, to Telegram.
 * Health alerts fire on state changes only (up to down, down to recovered), so a dependency that stays down
 * produces one message, not one per minute.
 */
@Service
public class AlertingService {

    private static final Logger logger = LoggerFactory.getLogger(AlertingService.class);

    private final MeterRegistry meterRegistry;
    private final EmailService emailService;
    private final MonitoringService monitoringService;

    @Value("${adproject.alerts.enabled:false}")
    private boolean alertsEnabled;

    @Value("${adproject.alerts.recipient-email:admin@example.com}")
    private String recipientEmail;

    @Value("${adproject.alerts.threshold.critical-errors:5}")
    private int criticalErrorThreshold;

    @Value("${adproject.alerts.telegram.bot-token:}")
    private String telegramBotToken;

    @Value("${adproject.alerts.telegram.chat-id:}")
    private String telegramChatId;

    @Value("${adproject.alerts.telegram.api-base:https://api.telegram.org}")
    private String telegramApiBase;

    @Value("${adproject.alerts.environment-label:production}")
    private String environmentLabel;

    // Last observed value per counter, to compute the increase between two checks
    private final ConcurrentMap<String, Double> lastObservedCounts = new ConcurrentHashMap<>();
    // Last observed level per health check, so only changes are announced
    private final ConcurrentMap<String, Level> lastLevels = new ConcurrentHashMap<>();

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public AlertingService(MeterRegistry meterRegistry, EmailService emailService, MonitoringService monitoringService) {
        this.meterRegistry = meterRegistry;
        this.emailService = emailService;
        this.monitoringService = monitoringService;
    }

    public boolean isTelegramConfigured() {
        return telegramBotToken != null && !telegramBotToken.isBlank()
                && telegramChatId != null && !telegramChatId.isBlank();
    }

    public boolean isEmailAlertConfigured() {
        return recipientEmail != null && !recipientEmail.isBlank() && !"admin@example.com".equalsIgnoreCase(recipientEmail.trim());
    }

    /** Tells the operator the app came up; doubles as confirmation that a deploy finished and Telegram works. */
    @EventListener(ApplicationReadyEvent.class)
    public void announceStartup() {
        if (alertsEnabled && isTelegramConfigured()) {
            sendTelegram("IAMS (" + environmentLabel + ") started and is accepting requests.");
        }
    }

    /** Every minute: error spikes. */
    @Scheduled(fixedRate = 60000)
    public void checkMetricsAndSendAlerts() {
        if (!alertsEnabled) {
            return;
        }
        checkCounterThreshold("adproject.critical.errors.total", "Critical Server Errors (5xx)", criticalErrorThreshold);
        // One schema fault is already a problem: the same code path will keep failing for every user.
        checkCounterThreshold("adproject.schema.incidents.total", "Database schema faults", 1);
    }

    /** Every minute: dependency health, announcing changes only. */
    @Scheduled(fixedDelay = 60000, initialDelay = 90000)
    public void checkHealthAndSendAlerts() {
        if (!alertsEnabled) {
            return;
        }
        try {
            evaluateHealth(monitoringService.snapshot().checks());
        } catch (Exception e) {
            logger.warn("Health watcher failed: {}", e.getMessage());
        }
    }

    /** Package-visible so tests can feed checks directly. */
    void evaluateHealth(List<Check> checks) {
        for (Check check : checks) {
            Level previous = lastLevels.put(check.name(), check.level());
            if (check.level() != Level.UP && previous != check.level()) {
                String label = check.level() == Level.DOWN || isOptionalServiceDown(check) ? "DOWN" : "WARNING";
                notifyAll("[" + label + "] " + check.name(), check.detail());
            } else if (check.level() == Level.UP && previous != null && previous != Level.UP) {
                notifyAll("[RECOVERED] " + check.name(), check.name() + " is healthy again. " + check.detail());
            }
        }
    }

    /** The AI service only ever reports WARN (the rest of the system works without it), but unreachable is downtime to the operator. */
    private static boolean isOptionalServiceDown(Check check) {
        return "AI service".equals(check.name()) && check.level() == Level.WARN;
    }

    private void checkCounterThreshold(String metricName, String alertDescription, int threshold) {
        Counter counter = meterRegistry.find(metricName).counter();
        if (counter == null) {
            logger.warn("Metric counter '{}' not found in MeterRegistry.", metricName);
            return;
        }
        double currentTotal = counter.count();
        double lastTotal = lastObservedCounts.getOrDefault(metricName, 0.0);
        double increase = currentTotal - lastTotal;
        lastObservedCounts.put(metricName, currentTotal);

        if (increase >= threshold) {
            String subject = "[ALERT] " + alertDescription + " exceeded threshold";
            String body = String.format("%d new event(s) in the last minute (threshold %d). Total since start: %.0f.",
                    (int) increase, threshold, currentTotal);
            String lastSchema = metricName.contains("schema") ? monitoringService.getLastSchemaIncident() : null;
            if (lastSchema != null) {
                body += "\nLast fault: " + lastSchema;
            }
            notifyAll(subject, body);
            logger.warn("ALERT TRIGGERED: {} - {}", alertDescription, body);
        }
    }

    /** Sends to every configured channel. Returns a short summary of what was attempted, for the test button. */
    public String notifyAll(String subject, String body) {
        StringBuilder result = new StringBuilder();
        if (isEmailAlertConfigured()) {
            try {
                emailService.sendAlertEmail(recipientEmail, subject, body);
                result.append("email sent; ");
            } catch (Exception e) {
                result.append("email failed; ");
                logger.warn("Alert email failed: {}", e.getMessage());
            }
        } else {
            result.append("email not configured; ");
        }
        if (isTelegramConfigured()) {
            result.append(sendTelegram(subject + "\n" + body) ? "telegram sent" : "telegram failed");
        } else {
            result.append("telegram not configured");
        }
        return result.toString();
    }

    boolean sendTelegram(String text) {
        if (!isTelegramConfigured()) {
            return false;
        }
        String message = "[" + environmentLabel + "] " + text;
        if (message.length() > 3500) {
            message = message.substring(0, 3500) + "...";
        }
        try {
            String form = "chat_id=" + URLEncoder.encode(telegramChatId.trim(), StandardCharsets.UTF_8)
                    + "&text=" + URLEncoder.encode(message, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            telegramApiBase.replaceAll("/+$", "") + "/bot" + telegramBotToken.trim() + "/sendMessage"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return true;
            }
            // The body explains the cause (bad chat id, bot not in the chat...) and never contains the token.
            logger.warn("Telegram alert rejected: HTTP {} {}", response.statusCode(), response.body());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            // Deliberately not logging the exception message: HTTP client errors can echo the request URL, which
            // contains the bot token.
            logger.warn("Telegram alert could not be sent ({})", e.getClass().getSimpleName());
            return false;
        }
    }

    // --- Every error, to Telegram ---------------------------------------------------------------------------------

    /** The same error again within this time is counted, not re-sent; the count goes out with the next message. */
    static final Duration ERROR_COALESCE = Duration.ofMinutes(5);
    /** Telegram throttles bots, and a storm of messages hides the first one. More than this per window is counted and reported. */
    static final int MAX_ERROR_MESSAGES_PER_WINDOW = 20;
    static final Duration ERROR_WINDOW = Duration.ofMinutes(10);
    private static final int MAX_ERROR_BUCKETS = 500;

    private static final class ErrorBucket {
        Instant lastSent;
        int suppressed;
        String sample = "";
    }

    private final java.util.Map<String, ErrorBucket> errorBuckets = new java.util.LinkedHashMap<>();
    private final Deque<Instant> errorSendTimes = new ArrayDeque<>();
    private final AtomicInteger heldBackByFloodLimit = new AtomicInteger();
    Clock clock = Clock.systemUTC();
    /** Sends happen off the request thread; tests replace this with a direct executor. */
    Executor dispatcher = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(100), runnable -> {
        Thread t = new Thread(runnable, "telegram-error-alerts");
        t.setDaemon(true);
        return t;
    }, new ThreadPoolExecutor.DiscardPolicy());

    @PostConstruct
    void registerErrorRelay() {
        ErrorAlertAppender.setSink(this::reportError);
    }

    /** Says goodbye on a graceful stop, so a restart is visible. A crash cannot say it; the uptime watcher covers that. */
    @EventListener(ContextClosedEvent.class)
    public void announceShutdown() {
        ErrorAlertAppender.setSink(null);
        if (alertsEnabled && isTelegramConfigured()) {
            sendTelegram("IAMS (" + environmentLabel + ") is shutting down.");
        }
    }

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern SECRET = Pattern.compile("(?i)(password|passwd|pwd|secret|token|api[_-]?key|authorization)(\\s*[=:]\\s*)\\S+");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+\\S+");

    /** Keeps secrets and personal data out of a chat: emails, anything that looks like a credential, and long messages. */
    static String redact(String text, int max) {
        if (text == null) {
            return "";
        }
        String clean = BEARER.matcher(SECRET.matcher(EMAIL.matcher(text).replaceAll("[email]")).replaceAll("$1$2[hidden]"))
                .replaceAll("Bearer [hidden]").replaceAll("\\s+", " ").trim();
        return clean.length() > max ? clean.substring(0, max) + "..." : clean;
    }

    private static String signature(ErrorReport r) {
        String message = r.exceptionMessage() != null ? r.exceptionMessage() : r.message();
        String normalised = message == null ? "" : message.toLowerCase().replaceAll("[0-9]+", "#").replaceAll("\\s+", " ");
        return r.logger() + "|" + r.exceptionClass() + "|" + (normalised.length() > 80 ? normalised.substring(0, 80) : normalised);
    }

    static String describe(ErrorReport r) {
        StringBuilder text = new StringBuilder("[ERROR] ").append(r.logger().substring(r.logger().lastIndexOf('.') + 1));
        text.append('\n').append(redact(r.message(), 300));
        if (r.exceptionClass() != null) {
            text.append('\n').append(r.exceptionClass().substring(r.exceptionClass().lastIndexOf('.') + 1));
            if (r.exceptionMessage() != null) {
                text.append(": ").append(redact(r.exceptionMessage(), 300));
            }
        }
        if (r.rootCause() != null) {
            text.append("\nRoot cause: ").append(redact(r.rootCause(), 300));
        }
        if (r.where() != null) {
            text.append("\nat ").append(r.where());
        }
        if (r.requestId() != null) {
            String id = r.requestId().length() > 8 ? r.requestId().substring(0, 8) : r.requestId();
            text.append("\nReference: ").append(id.toUpperCase(java.util.Locale.ROOT));
        }
        return text.toString();
    }

    /** Called for every ERROR log event. Sends the first of each kind at once and counts the repeats. */
    void reportError(ErrorReport report) {
        if (!alertsEnabled || !isTelegramConfigured()) {
            return;
        }
        String text = null;
        synchronized (errorBuckets) {
            Instant now = clock.instant();
            if (errorBuckets.size() >= MAX_ERROR_BUCKETS) {
                errorBuckets.values().removeIf(b -> b.suppressed == 0 && Duration.between(b.lastSent, now).compareTo(Duration.ofHours(1)) > 0);
            }
            ErrorBucket bucket = errorBuckets.computeIfAbsent(signature(report), k -> new ErrorBucket());
            bucket.sample = report.logger().substring(report.logger().lastIndexOf('.') + 1) + ": "
                    + redact(report.exceptionClass() != null ? report.exceptionClass() : report.message(), 100);
            boolean due = bucket.lastSent == null || Duration.between(bucket.lastSent, now).compareTo(ERROR_COALESCE) >= 0;
            if (due && floodAllows(now)) {
                text = describe(report);
                if (bucket.suppressed > 0) {
                    text += "\n+" + bucket.suppressed + " more like this since the last alert";
                }
                int held = heldBackByFloodLimit.getAndSet(0);
                if (held > 0) {
                    text += "\n(" + held + " alert(s) were held back by the flood limit)";
                }
                bucket.suppressed = 0;
                bucket.lastSent = now;
            } else {
                bucket.suppressed++;
                if (due) {
                    heldBackByFloodLimit.incrementAndGet();
                }
            }
        }
        if (text != null) {
            String message = text;
            dispatcher.execute(() -> sendTelegram(message));
        }
    }

    private boolean floodAllows(Instant now) {
        while (!errorSendTimes.isEmpty() && Duration.between(errorSendTimes.peekFirst(), now).compareTo(ERROR_WINDOW) >= 0) {
            errorSendTimes.pollFirst();
        }
        if (errorSendTimes.size() >= MAX_ERROR_MESSAGES_PER_WINDOW) {
            return false;
        }
        errorSendTimes.addLast(now);
        return true;
    }

    /** Every minute: reports repeats that were counted but never followed by another message, so no error goes unreported. */
    @Scheduled(fixedDelay = 60000, initialDelay = 120000)
    public void flushCountedErrors() {
        if (!alertsEnabled || !isTelegramConfigured()) {
            return;
        }
        List<String> messages = new java.util.ArrayList<>();
        synchronized (errorBuckets) {
            Instant now = clock.instant();
            for (ErrorBucket bucket : errorBuckets.values()) {
                if (bucket.suppressed > 0 && bucket.lastSent != null
                        && Duration.between(bucket.lastSent, now).compareTo(ERROR_COALESCE) >= 0 && floodAllows(now)) {
                    messages.add("[ERROR] " + bucket.suppressed + " more of: " + bucket.sample + "\n(repeats since the last alert)");
                    bucket.suppressed = 0;
                    bucket.lastSent = now;
                }
            }
        }
        messages.forEach(m -> dispatcher.execute(() -> sendTelegram(m)));
    }
}
