package com.sahyatri.review.dto;

import com.sahyatri.review.entity.ReviewTag;

import java.util.List;

/**
 * The profile's review header (§7.21.5). {@code average}/{@code count} and every bar and tag count come from visible
 * BOOKING reviews only; {@code counts} drive the filter pills.
 */
public record ReviewsSummary(Double average, long count, Counts counts, List<Category> categories,
                             List<Mention> mentionedOften) {

    public record Counts(long all, long booking, long ownTrekker) {
    }

    /** {@code key}: OVERALL, SAFETY, ROUTE, COMMUNICATION or CARE; {@code answers}: reviews that rated it. */
    public record Category(String key, double average, long answers) {
    }

    public record Mention(ReviewTag tag, long count) {
    }
}
