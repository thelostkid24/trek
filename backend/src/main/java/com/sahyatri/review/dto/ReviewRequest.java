package com.sahyatri.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Writes or rewrites the review of a booking. The words are optional. */
public record ReviewRequest(@NotNull @Min(1) @Max(5) Integer rating, @Size(max = 2000) String body) {
}
