package com.sahyatri.guide.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** "Career in the mountains": a year and a line. Replaced as a set, kept in the guide's order. */
@Entity
@Table(name = "guide_milestones")
public class GuideMilestone {

    @Id
    private UUID id;

    private UUID guideId;

    private int year;

    private String text;

    private int position;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected GuideMilestone() {
    }

    public GuideMilestone(UUID guideId, int year, String text, int position) {
        this.id = UUID.randomUUID();
        this.guideId = guideId;
        this.year = year;
        this.text = text;
        this.position = position;
        this.createdAt = Instant.now();
    }

    public UUID getGuideId() {
        return guideId;
    }

    public int getYear() {
        return year;
    }

    public String getText() {
        return text;
    }

    public int getPosition() {
        return position;
    }
}
