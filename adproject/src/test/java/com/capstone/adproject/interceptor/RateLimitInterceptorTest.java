package com.capstone.adproject.interceptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Refill;

class RateLimitInterceptorTest {

    private RateLimitInterceptor interceptorDirect;
    private RateLimitInterceptor interceptorBehindProxy;

    @BeforeEach
    void setUp() {
        Bandwidth defaultLimit = Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1)));
        Bandwidth authLimit = Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(1)));

        interceptorDirect = new RateLimitInterceptor(defaultLimit, authLimit, false);
        interceptorBehindProxy = new RateLimitInterceptor(defaultLimit, authLimit, true);
    }

    @Test
    void testAuthEndpointAllowsFiveAttemptsAndBlocksSixth() throws Exception {
        String clientIp = "192.168.1.10";

        for (int i = 1; i <= 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
            request.setRemoteAddr(clientIp);
            MockHttpServletResponse response = new MockHttpServletResponse();

            boolean allowed = interceptorDirect.preHandle(request, response, new Object());
            assertTrue(allowed, "Attempt " + i + " should be allowed");
            assertEquals(200, response.getStatus());
        }

        // 6th attempt should be blocked
        MockHttpServletRequest requestBlocked = new MockHttpServletRequest("GET", "/login");
        requestBlocked.setRemoteAddr(clientIp);
        MockHttpServletResponse responseBlocked = new MockHttpServletResponse();

        boolean allowedBlocked = interceptorDirect.preHandle(requestBlocked, responseBlocked, new Object());
        assertFalse(allowedBlocked, "6th attempt should be blocked");
        assertEquals(429, responseBlocked.getStatus());
        assertTrue(responseBlocked.getHeaderNames().contains("X-Rate-Limit-Retry-After-Seconds"));
    }

    @Test
    void testForgotPasswordEndpointRateLimiting() throws Exception {
        String clientIp = "10.0.0.50";

        for (int i = 1; i <= 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/forgot_password");
            request.setRemoteAddr(clientIp);
            MockHttpServletResponse response = new MockHttpServletResponse();

            boolean allowed = interceptorDirect.preHandle(request, response, new Object());
            assertTrue(allowed, "Attempt " + i + " should be allowed");
        }

        MockHttpServletRequest requestBlocked = new MockHttpServletRequest("POST", "/forgot_password");
        requestBlocked.setRemoteAddr(clientIp);
        MockHttpServletResponse responseBlocked = new MockHttpServletResponse();

        boolean allowedBlocked = interceptorDirect.preHandle(requestBlocked, responseBlocked, new Object());
        assertFalse(allowedBlocked, "6th attempt should be blocked by auth rate limiter");
        assertEquals(429, responseBlocked.getStatus());
    }

    @Test
    void testIpSpoofingPreventedWhenBehindProxyIsFalse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
        request.setRemoteAddr("192.168.1.100");
        request.addHeader("X-Forwarded-For", "203.0.113.195, 10.0.0.1");

        String resolvedIp = interceptorDirect.getClientIp(request);
        assertEquals("192.168.1.100", resolvedIp, "When behindProxy is false, X-Forwarded-For must be ignored to prevent IP spoofing");
    }

    @Test
    void testIpv6ClientIpParsedWhenBehindProxyIsTrue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "2001:0db8:85a3:0000:0000:8a2e:0370:7334, 10.0.0.2");

        String resolvedIp = interceptorBehindProxy.getClientIp(request);
        assertEquals("2001:0db8:85a3:0000:0000:8a2e:0370:7334", resolvedIp, "IPv6 address from X-Forwarded-For must be properly parsed without dot-check rejection");
    }

    @Test
    void testIpv6DirectAddressHandled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
        request.setRemoteAddr("::1");

        String resolvedIp = interceptorDirect.getClientIp(request);
        assertEquals("::1", resolvedIp);
    }
}
