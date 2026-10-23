package com.sahyatri.guide.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** "Career in the mountains": a date as exact as known, and a line. Replaced as a set, kept in the guide's order. */
@Entity
@Table(name = "guide_milestones")
public class GuideMilestone {

    @Id
    private UUID id;

    private UUID guideId;

    private Integer year;

    private Integer month;

    private Integer day;

    private boolean approximate;

    private String text;

    private int position;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected GuideMilestone() {
    }

    public GuideMilestone(UUID guideId, Integer year, Integer month, Integer day, boolean approximate, String text,
                          int position) {
        this.id = UUID.randomUUID();
        this.guideId = guideId;
        this.year = year;
        this.month = month;
        this.day = day;
        this.approximate = approximate;
        this.text = text;
        this.position = position;
        this.createdAt = Instant.now();
    }

    public UUID getGuideId() {
        return guideId;
    }

    public Integer getYear() {
        return year;
    }

    public Integer getMonth() {
        return month;
    }

    public Integer getDay() {
        return day;
    }

    public boolean isApproximate() {
        return approximate;
    }

    public String getText() {
        return text;
    }

    public int getPosition() {
        return position;
    }
}
