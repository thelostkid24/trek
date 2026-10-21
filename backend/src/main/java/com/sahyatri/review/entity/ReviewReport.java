package com.sahyatri.review.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A guide flagging a review for admins. Open until an admin hides, unhides or dismisses. */
@Entity
@Table(name = "review_reports")
public class ReviewReport {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID reviewId;

    @Column(nullable = false, updatable = false)
    private UUID reporterId;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant resolvedAt;

    protected ReviewReport() {
    }

    public ReviewReport(UUID reviewId, UUID reporterId, String reason) {
        this.id = UUID.randomUUID();
        this.reviewId = reviewId;
        this.reporterId = reporterId;
        this.reason = reason;
        this.createdAt = Instant.now();
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public void resolve(Instant at) {
        this.resolvedAt = at;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReviewId() {
        return reviewId;
    }

    public UUID getReporterId() {
        return reporterId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
