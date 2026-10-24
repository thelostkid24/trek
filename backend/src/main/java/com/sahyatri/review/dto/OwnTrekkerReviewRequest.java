package com.sahyatri.review.dto;

import com.sahyatri.review.entity.ReviewTag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A review from the guide's own trekker (§7.21.5). No email or phone: it waits for an admin to approve it.
 * {@code otherTag}: what they typed after picking "Other" under "What stood out?".
 */
public record OwnTrekkerReviewRequest(
        @NotNull @Min(1) @Max(5) Integer rating,
        @Min(1) @Max(5) Integer safetyRating, @Min(1) @Max(5) Integer routeRating,
        @Min(1) @Max(5) Integer communicationRating, @Min(1) @Max(5) Integer careRating,
        @Size(max = 6) List<@NotNull ReviewTag> tags,
        @Size(max = 60) String otherTag,
        @Size(max = 2000) String body,
        @NotBlank @Size(max = 60) String name,
        @Size(max = 100) String trekName) {
}
