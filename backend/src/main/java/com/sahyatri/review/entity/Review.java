package com.sahyatri.review.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A trekker's rating of a completed departure's guide. One per booking; the trekker may edit it. */
@Entity
@Table(name = "reviews")
public class Review {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID bookingId;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private UUID departureId;

    @Column(nullable = false, updatable = false)
    private UUID guideId;

    @Column(nullable = false, updatable = false)
    private UUID trackId;

    private int rating;

    private String body;

    /** First name shown with the review, taken from the booking when it was written. */
    @Column(nullable = false)
    private String authorName;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Review() {
    }

    public Review(UUID bookingId, UUID userId, UUID departureId, UUID guideId, UUID trackId, String authorName) {
        this.id = UUID.randomUUID();
        this.bookingId = bookingId;
        this.userId = userId;
        this.departureId = departureId;
        this.guideId = guideId;
        this.trackId = trackId;
        this.authorName = authorName;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public void rate(int rating, String body) {
        this.rating = rating;
        this.body = body;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getDepartureId() {
        return departureId;
    }

    public UUID getGuideId() {
        return guideId;
    }

    public UUID getTrackId() {
        return trackId;
    }

    public int getRating() {
        return rating;
    }

    public String getBody() {
        return body;
    }

    public String getAuthorName() {
        return authorName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
