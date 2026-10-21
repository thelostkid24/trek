package com.sahyatri.review.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A rating of a guide (§7.14, §7.21.5). BOOKING: one per booking of a completed departure. OWN_TREKKER: one per guide
 * and verified email or phone. The reviewer may rewrite it; the guide may reply once; an admin may hide either.
 */
@Entity
@Table(name = "reviews")
public class Review {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ReviewSource source;

    @Column(updatable = false)
    private UUID bookingId;

    @Column(updatable = false)
    private UUID userId;

    @Column(updatable = false)
    private UUID departureId;

    @Column(nullable = false, updatable = false)
    private UUID guideId;

    @Column(updatable = false)
    private UUID trackId;

    @Column(updatable = false)
    private String reviewerEmail;

    @Column(updatable = false)
    private String reviewerPhone;

    /** OWN_TREKKER only: the trek as the reviewer named it. */
    private String trekName;

    private int rating;

    private Integer safetyRating;

    private Integer routeRating;

    private Integer communicationRating;

    private Integer careRating;

    private String body;

    /** First name shown with the review. */
    @Column(nullable = false)
    private String authorName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "review_tags", joinColumns = @JoinColumn(name = "review_id"))
    @Column(name = "tag")
    @Enumerated(EnumType.STRING)
    private Set<ReviewTag> tags = new HashSet<>();

    private String replyBody;

    private Instant replyCreatedAt;

    private Instant replyUpdatedAt;

    private Instant hiddenAt;

    private String hiddenReason;

    private Instant replyHiddenAt;

    private String replyHiddenReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Review() {
    }

    public Review(UUID bookingId, UUID userId, UUID departureId, UUID guideId, UUID trackId, String authorName) {
        this.id = UUID.randomUUID();
        this.source = ReviewSource.BOOKING;
        this.bookingId = bookingId;
        this.userId = userId;
        this.departureId = departureId;
        this.guideId = guideId;
        this.trackId = trackId;
        this.authorName = authorName;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    /** A review from the guide's own trekker, proven by exactly one of email or phone. */
    public static Review ownTrekker(UUID guideId, String email, String phone) {
        Review r = new Review();
        r.id = UUID.randomUUID();
        r.source = ReviewSource.OWN_TREKKER;
        r.guideId = guideId;
        r.reviewerEmail = email;
        r.reviewerPhone = phone;
        r.createdAt = Instant.now();
        r.updatedAt = r.createdAt;
        return r;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public void rate(int rating, String body) {
        this.rating = rating;
        this.body = body;
    }

    public void rateDetails(Integer safety, Integer route, Integer communication, Integer care,
                            Collection<ReviewTag> tags) {
        this.safetyRating = safety;
        this.routeRating = route;
        this.communicationRating = communication;
        this.careRating = care;
        this.tags.clear();
        if (tags != null && !tags.isEmpty()) {
            this.tags.addAll(EnumSet.copyOf(tags));
        }
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public void setTrekName(String trekName) {
        this.trekName = trekName;
    }

    public void reply(String body, Instant at) {
        if (replyBody == null) {
            replyCreatedAt = at;
        }
        replyBody = body;
        replyUpdatedAt = at;
    }

    /** Removes the reply; an admin's hide goes with it. */
    public void removeReply() {
        replyBody = null;
        replyCreatedAt = null;
        replyUpdatedAt = null;
        replyHiddenAt = null;
        replyHiddenReason = null;
    }

    public void hide(String reason, Instant at) {
        hiddenAt = at;
        hiddenReason = reason;
    }

    public void unhide() {
        hiddenAt = null;
        hiddenReason = null;
    }

    public void hideReply(String reason, Instant at) {
        replyHiddenAt = at;
        replyHiddenReason = reason;
    }

    public void unhideReply() {
        replyHiddenAt = null;
        replyHiddenReason = null;
    }

    public UUID getId() {
        return id;
    }

    public ReviewSource getSource() {
        return source;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getUserId() {
        return userId;
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

    public String getReviewerEmail() {
        return reviewerEmail;
    }

    public String getReviewerPhone() {
        return reviewerPhone;
    }

    public String getTrekName() {
        return trekName;
    }

    public int getRating() {
        return rating;
    }

    public Integer getSafetyRating() {
        return safetyRating;
    }

    public Integer getRouteRating() {
        return routeRating;
    }

    public Integer getCommunicationRating() {
        return communicationRating;
    }

    public Integer getCareRating() {
        return careRating;
    }

    public String getBody() {
        return body;
    }

    public String getAuthorName() {
        return authorName;
    }

    public Set<ReviewTag> getTags() {
        return tags;
    }

    public String getReplyBody() {
        return replyBody;
    }

    public Instant getReplyCreatedAt() {
        return replyCreatedAt;
    }

    public Instant getReplyUpdatedAt() {
        return replyUpdatedAt;
    }

    public Instant getHiddenAt() {
        return hiddenAt;
    }

    public String getHiddenReason() {
        return hiddenReason;
    }

    public Instant getReplyHiddenAt() {
        return replyHiddenAt;
    }

    public String getReplyHiddenReason() {
        return replyHiddenReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
