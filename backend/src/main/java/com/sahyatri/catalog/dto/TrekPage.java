package com.sahyatri.catalog.dto;

import com.sahyatri.catalog.entity.ContentKind;
import com.sahyatri.snow.dto.CrowdCount;
import com.sahyatri.snow.dto.SnowReportResponse;

import java.util.List;
import java.util.Map;

/**
 * Public trek page: the route, its upcoming departures (soonest first), the page's lists (shared first), the newest
 * snow report (null until one is filed), recent tent counts (oldest first), the current refund tiers and the
 * charity share (null when none is set).
 */
public record TrekPage(
        TrackDetail track,
        List<TrekDeparture> departures,
        Map<ContentKind, List<ContentItem>> content,
        SnowReportResponse snowReport,
        List<CrowdCount> crowd,
        List<RefundTier> refundTiers,
        Charity charity) {

    /** Refund of the booking amount when cancelled at least {@code minDaysBefore} days before the start. */
    public record RefundTier(int minDaysBefore, int refundBps) {
    }

    /** Included in the price, never added on top. */
    public record Charity(String name, int bps) {
    }
}
