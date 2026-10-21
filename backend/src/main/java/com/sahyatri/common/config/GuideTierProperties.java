package com.sahyatri.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Guide tier thresholds (`app.guide-tiers`, §7.21.3). Tiers are worked out on every read, never stored (law 8).
 * Treks are departures completed with us; ratings are the §7.14 reviews (average to one decimal).
 *
 * @param trail  Trail Guide: also needs a verified BMC
 * @param senior Senior Guide
 * @param summit Summit Guide: also needs a verified, unexpired AMC
 */
@ConfigurationProperties("app.guide-tiers")
public record GuideTierProperties(Threshold trail, Threshold senior, Threshold summit) {

    public GuideTierProperties {
        if (trail == null || senior == null || summit == null) {
            throw new IllegalStateException("app.guide-tiers needs trail, senior and summit");
        }
    }

    /** A tier is met with at least {@code minTreks} treks and, when {@code minReviews} > 0, that many reviews averaging {@code minRating}. */
    public record Threshold(int minTreks, double minRating, int minReviews) {
    }
}
