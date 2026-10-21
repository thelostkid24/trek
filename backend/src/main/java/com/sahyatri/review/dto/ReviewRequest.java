package com.sahyatri.review.dto;

import com.sahyatri.review.entity.ReviewTag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Writes or rewrites the review of a booking. Everything but the overall stars is optional. */
public record ReviewRequest(@NotNull @Min(1) @Max(5) Integer rating, @Size(max = 2000) String body,
                            @Min(1) @Max(5) Integer safetyRating, @Min(1) @Max(5) Integer routeRating,
                            @Min(1) @Max(5) Integer communicationRating, @Min(1) @Max(5) Integer careRating,
                            @Size(max = 6) List<@NotNull ReviewTag> tags) {
}
