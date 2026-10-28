package com.capstone.adproject.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RateLimitConfig {

    // Define a default rate limit: 10 requests per minute
    @Bean("defaultRateLimit")
    public Bandwidth defaultRateLimit() {
        return Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1)));
    }

    // Define an authentication rate limit: 5 attempts per minute
    @Bean("authRateLimit")
    public Bandwidth authRateLimit() {
        return Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(1)));
    }

}