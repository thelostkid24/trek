package com.sahyatri.review.dto;

/** The posted review and whether it was new (false = an earlier one from the same email or phone was rewritten). */
public record OwnTrekkerReviewResponse(PublicReview review, boolean created) {
}
