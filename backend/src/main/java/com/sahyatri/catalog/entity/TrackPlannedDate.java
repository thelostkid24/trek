package com.sahyatri.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A date a trek will run before its guide is confirmed (§7.20). Never bookable. Each track's list is replaced whole. */
@Entity
@Table(name = "track_planned_dates")
public class TrackPlannedDate {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID trackId;

    @Column(nullable = false)
    private LocalDate startDate;

    private String note;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected TrackPlannedDate() {
    }

    public TrackPlannedDate(UUID trackId, LocalDate startDate, String note) {
        this.id = UUID.randomUUID();
        this.trackId = trackId;
        this.startDate = startDate;
        this.note = note;
        this.createdAt = Instant.now();
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public String getNote() {
        return note;
    }
}
