package com.sahyatri.review.dto;

/** A guide's average rating (one decimal) and how many reviews it is built from. Null average when none. */
public record RatingSummary(Double average, long count) {

    public static final RatingSummary NONE = new RatingSummary(null, 0);
}
