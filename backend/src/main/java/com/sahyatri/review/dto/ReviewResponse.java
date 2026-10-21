package com.sahyatri.review.dto;

import com.sahyatri.review.entity.Review;
import com.sahyatri.review.entity.ReviewTag;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The trekker's own review of a booking. */
public record ReviewResponse(UUID id, UUID bookingId, int rating, String body, Integer safetyRating,
                             Integer routeRating, Integer communicationRating, Integer careRating,
                             List<ReviewTag> tags, Instant createdAt, Instant updatedAt) {

    public static ReviewResponse of(Review r) {
        return new ReviewResponse(r.getId(), r.getBookingId(), r.getRating(), r.getBody(), r.getSafetyRating(),
                r.getRouteRating(), r.getCommunicationRating(), r.getCareRating(), PublicReview.sortedTags(r),
                r.getCreatedAt(), r.getUpdatedAt());
    }
}
