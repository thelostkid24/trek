package com.sahyatri.insights.dto;

import java.time.Instant;
import java.util.List;

/**
 * The admin Insights dashboard (§7.15): everything computed from the database on each request, nothing stored.
 * {@code from}–{@code to} is the window; guides, upcoming departures and marketing reach are not windowed.
 */
public record InsightsResponse(
        int days,
        Instant from,
        Instant to,
        Headline headline,
        List<DailyPoint> daily,
        List<FunnelStep> funnel,
        List<SourceRow> sources,
        List<SourceRow> campaigns,
        List<CountRow> heardFrom,
        List<CountRow> signupMethods,
        List<CountRow> devices,
        PaymentSummary payments,
        List<TrekRow> treks,
        List<UpcomingDeparture> upcoming,
        List<GuideRow> guides,
        MarketingReach marketingReach) {
}
