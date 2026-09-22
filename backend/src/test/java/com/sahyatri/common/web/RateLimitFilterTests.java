package com.sahyatri.common.web;

import com.sahyatri.common.config.RateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTests {

    private final MutableClock clock = new MutableClock();
    private final RateLimitFilter filter = new RateLimitFilter(new RateLimitProperties(
            true, "CloudFront-Viewer-Address", 100,
            List.of("/api/webhooks/**"),
            List.of(new RateLimitProperties.Rule(List.of("/api/auth/signup", "/api/auth/login"), List.of(), 3),
                    new RateLimitProperties.Rule(List.of("/api/public/bookings"), List.of("POST"), 2))),
            clock);

    @Test
    void blocksAfterLimitWithStandardErrorShape() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(call("POST", "/api/auth/login", "1.1.1.1:443").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse blocked = call("POST", "/api/auth/login", "1.1.1.1:443");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("20");
        assertThat(blocked.getContentAsString()).contains("\"code\":\"RATE_LIMITED\"", "\"retry_after\":20");
    }

    @Test
    void refillsOverTime() throws Exception {
        for (int i = 0; i < 3; i++) {
            call("POST", "/api/auth/signup", "2.2.2.2:1");
        }
        assertThat(call("POST", "/api/auth/signup", "2.2.2.2:1").getStatus()).isEqualTo(429);
        clock.advance(20_000);
        assertThat(call("POST", "/api/auth/signup", "2.2.2.2:1").getStatus()).isEqualTo(200);
    }

    @Test
    void limitsArePerClientIpAndPerRule() throws Exception {
        for (int i = 0; i < 3; i++) {
            call("POST", "/api/auth/login", "3.3.3.3:1");
        }
        assertThat(call("POST", "/api/auth/login", "3.3.3.3:1").getStatus()).isEqualTo(429);
        assertThat(call("POST", "/api/auth/login", "[2001:db8::1]:1").getStatus()).isEqualTo(200);
        assertThat(call("GET", "/api/auth/me", "3.3.3.3:1").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/public/bookings", "3.3.3.3:1").getStatus()).isEqualTo(200);
    }

    @Test
    void methodFilterAndExemptions() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(call("GET", "/api/public/bookings", "4.4.4.4:1").getStatus()).isEqualTo(200);
            assertThat(call("POST", "/api/webhooks/razorpay", "4.4.4.4:1").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void ignoresXForwardedForAndUsesEdgeHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addHeader("X-Forwarded-For", "9.9.9.9");
        request.addHeader("CloudFront-Viewer-Address", "5.5.5.5:1234");
        assertThat(filter.clientIp(request)).isEqualTo("5.5.5.5");
        request.removeHeader("CloudFront-Viewer-Address");
        assertThat(filter.clientIp(request)).isEqualTo(request.getRemoteAddr());
    }

    private MockHttpServletResponse call(String method, String path, String viewer) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("CloudFront-Viewer-Address", viewer);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static final class MutableClock extends Clock {
        private long millis = 1_700_000_000_000L;

        void advance(long ms) {
            millis += ms;
        }

        @Override
        public long millis() {
            return millis;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
