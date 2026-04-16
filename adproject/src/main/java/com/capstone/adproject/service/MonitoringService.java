package com.capstone.adproject.service;

import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Service for tracking custom application metrics using Micrometer.
 * This allows us to increment counters for events like critical errors or failed signups,
 * which can then be monitored by the AlertingService.
 */
@Service
public class MonitoringService {

    private final Counter criticalErrorCounter;
    private final Counter userSignupFailureCounter;
    // Add other counters for metrics you want to monitor
    // private final Counter failedPaymentCounter;
    // private final Counter latencyJumpCounter; // Note: Latency usually tracked by Timer, not Counter

    public MonitoringService(MeterRegistry meterRegistry) {
        // Initialize counters. Naming convention: 'adproject.<metric_name>.total'
        // Description is important for understanding the metric later.
        this.criticalErrorCounter = Counter.builder("adproject.critical.errors.total")
                .description("Total count of unexpected 5xx server errors caught by GlobalExceptionHandler")
                .register(meterRegistry);
        this.userSignupFailureCounter = Counter.builder("adproject.user.signup.failures.total")
                .description("Total count of failed user signup attempts")
                .register(meterRegistry);
    }

    public void incrementCriticalErrorCount() {
        criticalErrorCounter.increment();
    }

    public void incrementUserSignupFailureCount() {
        userSignupFailureCounter.increment();
    }
}