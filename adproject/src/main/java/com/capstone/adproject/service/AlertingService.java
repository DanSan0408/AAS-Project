package com.capstone.adproject.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Service responsible for periodically checking application metrics
 * and sending alerts if configured thresholds are breached.
 */
@Service
public class AlertingService {

    private static final Logger logger = LoggerFactory.getLogger(AlertingService.class);

    private final MeterRegistry meterRegistry;
    private final EmailService emailService;

    @Value("${adproject.alerts.enabled:false}")
    private boolean alertsEnabled;

    @Value("${adproject.alerts.recipient-email:admin@example.com}")
    private String recipientEmail;

    @Value("${adproject.alerts.threshold.critical-errors:5}")
    private int criticalErrorThreshold;

    // Store the last observed count for 'critical.errors.total' to calculate the rate
    private final ConcurrentMap<String, Double> lastObservedCounts = new ConcurrentHashMap<>();

    public AlertingService(MeterRegistry meterRegistry, EmailService emailService) {
        this.meterRegistry = meterRegistry;
        this.emailService = emailService;
    }

    /**
     * Scheduled task to check for alert conditions every minute.
     * Runs every 60 seconds.
     */
    @Scheduled(fixedRate = 60000)
    public void checkMetricsAndSendAlerts() {
        if (!alertsEnabled) {
            // logger.debug("Alerting system is disabled.");
            return;
        }

        // Check 5xx error spikes
        checkCounterThreshold("adproject.critical.errors.total", "Critical Server Errors (5xx)", criticalErrorThreshold);
        // Add checks for other metrics as they are implemented
        // checkCounterThreshold("adproject.user.signup.failures.total", "User Signup Failures", signupFailureThreshold);
    }

    private void checkCounterThreshold(String metricName, String alertDescription, int threshold) {
        // Find the counter by its name
        Counter counter = meterRegistry.find(metricName).counter();

        if (counter != null) {
            double currentTotal = counter.measure().iterator().next().getValue();
            double lastTotal = lastObservedCounts.getOrDefault(metricName, 0.0);

            // Calculate the increase in the last minute (since the scheduled task runs every minute)
            double increaseSinceLastCheck = currentTotal - lastTotal;
            lastObservedCounts.put(metricName, currentTotal); // Update last observed count

            if (increaseSinceLastCheck >= threshold) {
                String subject = "[ALERT] High Severity: " + alertDescription + " Exceeded Threshold";
                String body = String.format("The number of %s detected in the last minute (%d) has exceeded the threshold of %d. Current total: %.0f",
                        alertDescription.toLowerCase(), (int) increaseSinceLastCheck, threshold, currentTotal);
                emailService.sendAlertEmail(recipientEmail, subject, body);
                logger.warn("ALERT TRIGGERED: {} - {}", alertDescription, body);
            }
        } else {
            logger.warn("Metric counter '{}' not found in MeterRegistry.", metricName);
        }
    }
}