package com.sahyatri.review.dto;

import com.sahyatri.review.entity.Review;
import com.sahyatri.review.entity.ReviewSource;
import com.sahyatri.review.entity.ReviewTag;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A review as anyone sees it: first name only, with the trek and when it ran (BOOKING) or the trek the reviewer
 * named (OWN_TREKKER, no date). {@code reply} is null when there is none or an admin hid it.
 */
public record PublicReview(UUID id, ReviewSource source, int rating, Integer safetyRating, Integer routeRating,
                           Integer communicationRating, Integer careRating, List<ReviewTag> tags, String body,
                           String authorName, String trekName, LocalDate trekStartDate, Instant createdAt,
                           Reply reply) {

    public record Reply(String body, Instant createdAt, Instant updatedAt) {
    }

    /** @param trekName the trek's name for BOOKING reviews; ignored for OWN_TREKKER */
    public static PublicReview of(Review r, String trekName, LocalDate trekStartDate, boolean showHiddenReply) {
        boolean booking = r.getSource() == ReviewSource.BOOKING;
        Reply reply = r.getReplyBody() == null || (r.getReplyHiddenAt() != null && !showHiddenReply) ? null
                : new Reply(r.getReplyBody(), r.getReplyCreatedAt(), r.getReplyUpdatedAt());
        return new PublicReview(r.getId(), r.getSource(), r.getRating(), r.getSafetyRating(), r.getRouteRating(),
                r.getCommunicationRating(), r.getCareRating(), sortedTags(r), r.getBody(), r.getAuthorName(),
                booking ? trekName : r.getTrekName(), booking ? trekStartDate : null, r.getCreatedAt(), reply);
    }

    static List<ReviewTag> sortedTags(Review r) {
        return r.getTags().stream().sorted().toList();
    }
}
