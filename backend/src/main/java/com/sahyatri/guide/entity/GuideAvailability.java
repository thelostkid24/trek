package com.sahyatri.guide.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Days a guide is free to lead (§7.21.7). Only admins see them. A guide's ranges never overlap (service). */
@Entity
@Table(name = "guide_availability")
public class GuideAvailability {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID guideId;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    private String note;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected GuideAvailability() {
    }

    public GuideAvailability(UUID guideId, LocalDate startDate, LocalDate endDate, String note) {
        this.id = UUID.randomUUID();
        this.guideId = guideId;
        this.createdAt = Instant.now();
        change(startDate, endDate, note);
    }

    public void change(LocalDate startDate, LocalDate endDate, String note) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.note = note;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGuideId() {
        return guideId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getNote() {
        return note;
    }
}
