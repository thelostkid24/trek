package com.sahyatri.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/**
 * Booking rules (`app.bookings`).
 *
 * @param holdTtl     how long seats stay held while the trekker pays
 * @param refundTiers trekker-cancellation refund by days before the start date; frozen onto each booking
 */
@ConfigurationProperties("app.bookings")
public record BookingProperties(Duration holdTtl, List<RefundTier> refundTiers) {

    public BookingProperties {
        if (holdTtl == null || holdTtl.compareTo(Duration.ofMinutes(2)) < 0) {
            throw new IllegalStateException("app.bookings.hold-ttl must be at least 2 minutes");
        }
        if (refundTiers == null || refundTiers.stream().noneMatch(t -> t.minDaysBefore() == 0)) {
            throw new IllegalStateException("app.bookings.refund-tiers needs a tier with min-days-before 0");
        }
        if (refundTiers.stream().anyMatch(t -> t.minDaysBefore() < 0 || t.refundBps() < 0 || t.refundBps() > 10_000)) {
            throw new IllegalStateException("app.bookings.refund-tiers: days >= 0 and bps 0-10000");
        }
        refundTiers = refundTiers.stream()
                .sorted(Comparator.comparingInt(RefundTier::minDaysBefore).reversed())
                .toList();
    }

    public record RefundTier(int minDaysBefore, int refundBps) {
    }
}
