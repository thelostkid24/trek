package com.sahyatri.catalog.entity;

import com.sahyatri.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A dated, priced run of a track with one guide. Status and seat changes happen only under
 * {@code DepartureRepository.findByIdForUpdate}; the table's CHECKs back up laws 2, 4 and 7.
 */
@Entity
@Table(name = "departures")
public class Departure {

    /** Law 2: at most 10 trekkers per guide. The table CHECK uses the same number. */
    public static final int MAX_GROUP_SIZE = 10;

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "track_id")
    private Track track;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guide_id")
    private User guide;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    private long pricePaise;

    private int maxGroupSize;

    private int seatsTaken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DepartureStatus status;

    private Integer guideShareBps;

    private Instant publishedAt;

    private Instant cancelledAt;

    @Enumerated(EnumType.STRING)
    private CancelReason cancelReasonCode;

    private String cancelReasonNote;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Departure() {
    }

    public static Departure draft() {
        Departure departure = new Departure();
        departure.id = UUID.randomUUID();
        departure.status = DepartureStatus.DRAFT;
        departure.createdAt = Instant.now();
        departure.updatedAt = departure.createdAt;
        return departure;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    /** Draft fields. The end date follows from the track's duration. */
    public void plan(Track track, User guide, LocalDate startDate, long pricePaise, int maxGroupSize) {
        this.track = track;
        this.guide = guide;
        this.startDate = startDate;
        this.endDate = startDate.plusDays(track.getDurationDays() - 1L);
        this.pricePaise = pricePaise;
        this.maxGroupSize = maxGroupSize;
    }

    public void publish(int guideShareBps) {
        this.status = DepartureStatus.PUBLISHED;
        this.guideShareBps = guideShareBps;
        this.publishedAt = Instant.now();
    }

    public void cancel(CancelReason reason, String note) {
        this.status = DepartureStatus.CANCELLED;
        this.cancelledAt = Instant.now();
        this.cancelReasonCode = reason;
        this.cancelReasonNote = note;
    }

    public void expire() {
        this.status = DepartureStatus.EXPIRED;
    }

    public void complete() {
        this.status = DepartureStatus.COMPLETED;
    }

    /** Caller holds the row lock and has checked {@link #seatsLeft()}; the DB CHECK is the backstop (law 2). */
    public void takeSeats(int seats) {
        if (seats > seatsLeft()) {
            throw new IllegalStateException("Not enough seats on departure " + id);
        }
        seatsTaken += seats;
    }

    public void releaseSeats(int seats) {
        seatsTaken = Math.max(0, seatsTaken - seats);
    }

    public int seatsLeft() {
        return maxGroupSize - seatsTaken;
    }

    public boolean isDraft() {
        return status == DepartureStatus.DRAFT;
    }

    public UUID getId() {
        return id;
    }

    public Track getTrack() {
        return track;
    }

    public User getGuide() {
        return guide;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public long getPricePaise() {
        return pricePaise;
    }

    public int getMaxGroupSize() {
        return maxGroupSize;
    }

    public int getSeatsTaken() {
        return seatsTaken;
    }

    public DepartureStatus getStatus() {
        return status;
    }

    public Integer getGuideShareBps() {
        return guideShareBps;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public CancelReason getCancelReasonCode() {
        return cancelReasonCode;
    }

    public String getCancelReasonNote() {
        return cancelReasonNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
