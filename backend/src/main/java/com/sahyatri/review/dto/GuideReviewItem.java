package com.sahyatri.review.dto;

/** One of the guide's own reviews, with what an admin did to it and whether this guide reported it. */
public record GuideReviewItem(PublicReview review, String hiddenReason, String replyHiddenReason, boolean reported) {
}
