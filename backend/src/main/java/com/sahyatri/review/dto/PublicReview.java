package com.sahyatri.review.dto;

import java.time.Instant;
import java.time.LocalDate;

/** A review as anyone sees it: first name only, with the trek and when it ran. */
public record PublicReview(int rating, String body, String authorName, String trekName, LocalDate trekStartDate,
                           Instant createdAt) {
}
