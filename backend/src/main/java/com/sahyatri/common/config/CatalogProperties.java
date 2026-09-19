package com.sahyatri.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

/**
 * Catalog rules (`app.catalog`).
 *
 * @param guideShareBps     guide's share of the booking amount, frozen on a departure at publish (law 7)
 * @param bookingCutoffDays bookings close this many days before the start date
 * @param zone              calendar used for trek dates
 */
@ConfigurationProperties("app.catalog")
public record CatalogProperties(int guideShareBps, int bookingCutoffDays, ZoneId zone) {

    public CatalogProperties {
        if (guideShareBps < 0 || guideShareBps > 10_000) {
            throw new IllegalStateException("app.catalog.guide-share-bps must be 0-10000");
        }
        if (bookingCutoffDays < 0) {
            throw new IllegalStateException("app.catalog.booking-cutoff-days must be >= 0");
        }
    }
}
