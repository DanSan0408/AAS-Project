package com.capstone.adproject.interceptor;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final LoadingCache<String, Bucket> buckets;

    @Autowired
    public RateLimitInterceptor(@Qualifier("defaultRateLimit") Bandwidth defaultRateLimit) {
        buckets = CacheBuilder.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS) // Cache buckets for 1 hour to prevent memory leaks for inactive IPs
                .build(new CacheLoader<String, Bucket>() {
                    @Override
                    public Bucket load(String key) {
                        // Create a new bucket for each IP address with the default rate limit
                        return Bucket.builder().addLimit(defaultRateLimit).build();
                    }
                });
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientIp = getClientIp(request);
        Bucket bucket = buckets.get(clientIp);
        long availableTokensBefore = bucket.getAvailableTokens();

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1); // Try to consume one token
        if (probe.isConsumed()) {
            log.info("Request from IP {}: token consumed for {}. Available before: {}, Available after: {}.",
                    clientIp, request.getRequestURI(), availableTokensBefore, probe.getRemainingTokens());
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            return true;
        } else {
            long nanosToWaitForRefill = probe.getNanosToWaitForRefill();
            long secondsToWait = TimeUnit.NANOSECONDS.toSeconds(nanosToWaitForRefill);
            if (secondsToWait == 0 && nanosToWaitForRefill > 0) {
                secondsToWait = 1; // Always suggest at least 1 second if there's any wait time.
            }
            log.warn("Request from IP {}: rate limit EXCEEDED for {}. Available tokens: {}. Wait for {} seconds.",
                    clientIp, request.getRequestURI(), availableTokensBefore, secondsToWait);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value()); // 429 Too Many Requests
            response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(secondsToWait));
            response.getWriter().write("Too many requests. Please try again after " + secondsToWait + " seconds.");
            return false;
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For"); // Check for X-Forwarded-For header (common in proxies/load balancers)
        if (xfHeader == null || xfHeader.isEmpty() || !xfHeader.contains(".")) {
            return request.getRemoteAddr(); // Fallback to direct remote address
        }
        return xfHeader.split(",")[0]; // Take the first IP in the list
    }
}