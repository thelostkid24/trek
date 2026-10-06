package com.sahyatri.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Per-client-IP request limits on /api/** (`app.rate-limit.*`). The first matching rule wins; requests that
 * match none use {@code defaultPerMinute}.
 *
 * @param clientIpHeader header carrying the real client IP, set by the edge (X-Forwarded-For behind the ALB,
 *                       CloudFront-Viewer-Address on CloudFront). Blank = the socket address. For X-Forwarded-For
 *                       only the last entry is used: the earlier ones are client-controlled.
 * @param exempt         Ant patterns never limited (webhooks, health)
 */
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        String clientIpHeader,
        int defaultPerMinute,
        List<String> exempt,
        List<Rule> rules) {

    /** @param patterns Ant patterns (a {name} segment is a variable matching anything, not alternation)
     *  @param methods HTTP methods this rule applies to; empty = all */
    public record Rule(List<String> patterns, List<String> methods, int perMinute) {
    }
}
