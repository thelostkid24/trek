package com.sahyatri.review.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A review as admins moderate it: who it's about, who wrote it, when it was approved (null = waiting), hidden state and
 * open reports.
 */
public record AdminReviewItem(PublicReview review, UUID guideId, String guideName, String guideSlug,
                              String reviewerContact, Instant approvedAt, Instant hiddenAt, String hiddenReason, Instant replyHiddenAt,
                              String replyHiddenReason, List<Report> reports) {

    public record Report(String reason, String reporterName, Instant createdAt) {
    }
}
