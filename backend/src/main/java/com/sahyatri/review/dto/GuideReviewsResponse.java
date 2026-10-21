package com.sahyatri.review.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Guide → Reviews (§7.21.5): the headline (BOOKING only), group-verified count, replies posted in the last 7 days,
 * the completed departures to ask about, and every review (hidden ones too), newest first.
 */
public record GuideReviewsResponse(String slug, String fullName, Double average, long count, long ownTrekkerCount,
                                   long repliedThisWeek, List<CompletedDeparture> departures,
                                   List<GuideReviewItem> items) {

    public record CompletedDeparture(UUID departureId, String trekName, LocalDate startDate) {
    }
}
