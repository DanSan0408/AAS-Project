package com.capstone.adproject.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RateLimitConfig {

    // Define a default rate limit: 10 requests per minute
    @Bean
    public Bandwidth defaultRateLimit() {
        return Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1)));
    }

}