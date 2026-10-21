package com.sahyatri.review.dto;

import java.util.List;

/** One page of a guide's visible reviews, newest first. */
public record ReviewPage(List<PublicReview> items, long total, int page, int size) {
}
