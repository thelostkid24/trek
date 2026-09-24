package com.sahyatri.review.dto;

import com.sahyatri.review.entity.Review;

import java.time.Instant;
import java.util.UUID;

/** The trekker's own review of a booking. */
public record ReviewResponse(UUID id, UUID bookingId, int rating, String body, Instant createdAt, Instant updatedAt) {

    public static ReviewResponse of(Review r) {
        return new ReviewResponse(r.getId(), r.getBookingId(), r.getRating(), r.getBody(), r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
