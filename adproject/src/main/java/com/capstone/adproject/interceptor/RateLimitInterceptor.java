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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final LoadingCache<String, Bucket> defaultBuckets;
    private final LoadingCache<String, Bucket> authBuckets;
    private final boolean behindProxy;

    @Autowired
    public RateLimitInterceptor(
            @Qualifier("defaultRateLimit") Bandwidth defaultRateLimit,
            @Qualifier("authRateLimit") Bandwidth authRateLimit,
            @Value("${app.security.behind-proxy:false}") boolean behindProxy) {
        this.behindProxy = behindProxy;
        this.defaultBuckets = CacheBuilder.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS) // Cache buckets for 1 hour to prevent memory leaks for inactive IPs
                .build(new CacheLoader<String, Bucket>() {
                    @Override
                    public Bucket load(String key) {
                        return Bucket.builder().addLimit(defaultRateLimit).build();
                    }
                });
        this.authBuckets = CacheBuilder.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build(new CacheLoader<String, Bucket>() {
                    @Override
                    public Bucket load(String key) {
                        return Bucket.builder().addLimit(authRateLimit).build();
                    }
                });
    }

    public RateLimitInterceptor(Bandwidth defaultRateLimit) {
        this(defaultRateLimit, defaultRateLimit, false);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientIp = getClientIp(request);
        boolean isAuth = isAuthEndpoint(request);
        Bucket bucket = isAuth ? authBuckets.get(clientIp) : defaultBuckets.get(clientIp);
        long availableTokensBefore = bucket.getAvailableTokens();

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1); // Try to consume one token
        if (probe.isConsumed()) {
            log.info("Request from IP {}: token consumed for {} (auth={}). Available before: {}, Available after: {}.",
                    clientIp, request.getRequestURI(), isAuth, availableTokensBefore, probe.getRemainingTokens());
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            return true;
        } else {
            long nanosToWaitForRefill = probe.getNanosToWaitForRefill();
            long secondsToWait = TimeUnit.NANOSECONDS.toSeconds(nanosToWaitForRefill);
            if (secondsToWait == 0 && nanosToWaitForRefill > 0) {
                secondsToWait = 1; // Always suggest at least 1 second if there's any wait time.
            }
            log.warn("Request from IP {}: rate limit EXCEEDED for {} (auth={}). Available tokens: {}. Wait for {} seconds.",
                    clientIp, request.getRequestURI(), isAuth, availableTokensBefore, secondsToWait);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value()); // 429 Too Many Requests
            response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(secondsToWait));
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("Too many requests. Please try again after " + secondsToWait + " seconds.");
            return false;
        }
    }

    private boolean isAuthEndpoint(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        String contextPath = request.getContextPath();
        String path = (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath))
                ? uri.substring(contextPath.length())
                : uri;

        return path.equals("/login")
                || path.startsWith("/login/")
                || path.equals("/forgot_password")
                || path.startsWith("/forgot_password/")
                || path.equals("/reset_password")
                || path.startsWith("/reset_password/");
    }

    public String getClientIp(HttpServletRequest request) {
        if (behindProxy) {
            String xfHeader = request.getHeader("X-Forwarded-For");
            if (xfHeader != null && !xfHeader.trim().isEmpty()) {
                String firstIp = xfHeader.split(",")[0].trim();
                if (!firstIp.isEmpty()) {
                    return firstIp;
                }
            }
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.trim().isEmpty()) ? remoteAddr.trim() : "unknown";
    }
}