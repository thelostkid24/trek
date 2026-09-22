package com.sahyatri.common.web;

import com.sahyatri.common.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token-bucket limits per client IP and rule. In-memory: correct while one backend task runs; move the
 * buckets to Postgres or Redis before scaling out. Runs right after Spring Security so a 429 still carries
 * CORS headers.
 */
@Component
@Order(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    static final String DEFAULT_RULE = "default";

    private final RateLimitProperties props;
    private final Clock clock;
    private final AntPathMatcher matcher = new AntPathMatcher();
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Autowired
    public RateLimitFilter(RateLimitProperties props) {
        this(props, Clock.systemUTC());
    }

    RateLimitFilter(RateLimitProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !props.enabled()
                || !path.startsWith("/api/")
                || "OPTIONS".equals(request.getMethod())
                || orEmpty(props.exempt()).stream().anyMatch(p -> matcher.match(p, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ruleKey = DEFAULT_RULE;
        int perMinute = props.defaultPerMinute();
        List<RateLimitProperties.Rule> rules = orEmpty(props.rules());
        for (int i = 0; i < rules.size(); i++) {
            RateLimitProperties.Rule rule = rules.get(i);
            if (orEmpty(rule.patterns()).stream().anyMatch(pt -> matcher.match(pt, request.getRequestURI()))
                    && (orEmpty(rule.methods()).isEmpty() || rule.methods().contains(request.getMethod()))) {
                ruleKey = "r" + i;
                perMinute = rule.perMinute();
                break;
            }
        }
        long nowMillis = clock.millis();
        int limit = perMinute;
        Bucket bucket = buckets.computeIfAbsent(ruleKey + "|" + clientIp(request), k -> new Bucket(limit, nowMillis));
        long waitMillis = bucket.tryTake(limit, nowMillis);
        if (waitMillis > 0) {
            long retryAfter = Math.max(1, (waitMillis + 999) / 1000);
            response.setStatus(429);
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests. Try again in a moment.\","
                    + "\"details\":{\"retry_after\":" + retryAfter + "}}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Drops buckets that have refilled completely; a fresh one behaves the same. */
    @Scheduled(fixedDelay = 300_000)
    void evictIdle() {
        long now = clock.millis();
        buckets.values().removeIf(b -> b.isFull(now));
    }

    String clientIp(HttpServletRequest request) {
        String header = props.clientIpHeader();
        if (header != null && !header.isBlank()) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                // CloudFront-Viewer-Address is "ip:port" (IPv6 too): strip the port.
                int colon = value.lastIndexOf(':');
                return colon > 0 ? value.substring(0, colon) : value;
            }
        }
        return request.getRemoteAddr();
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    /** Capacity = perMinute tokens, refilled continuously at perMinute per 60 s. */
    static final class Bucket {
        private final int capacity;
        private double tokens;
        private long updatedMillis;

        Bucket(int capacity, long nowMillis) {
            this.capacity = capacity;
            this.tokens = capacity;
            this.updatedMillis = nowMillis;
        }

        /** Takes one token; returns 0, or the millis until one is available. */
        synchronized long tryTake(int perMinute, long nowMillis) {
            refill(nowMillis);
            if (tokens >= 1) {
                tokens -= 1;
                return 0;
            }
            return (long) Math.ceil((1 - tokens) * 60_000d / perMinute);
        }

        synchronized boolean isFull(long nowMillis) {
            refill(nowMillis);
            return tokens >= capacity;
        }

        private void refill(long nowMillis) {
            long elapsed = Math.max(0, nowMillis - updatedMillis);
            tokens = Math.min(capacity, tokens + elapsed * capacity / 60_000d);
            updatedMillis = nowMillis;
        }
    }
}
