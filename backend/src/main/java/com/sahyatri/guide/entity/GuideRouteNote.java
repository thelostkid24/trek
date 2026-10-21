package com.sahyatri.guide.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** A line in the guide's words about a route they lead. Replaced as a set. */
@Entity
@Table(name = "guide_route_notes")
@IdClass(GuideRouteNote.Key.class)
public class GuideRouteNote {

    @Id
    private UUID guideId;

    @Id
    private UUID trackId;

    private String note;

    @Column(nullable = false)
    private Instant updatedAt;

    protected GuideRouteNote() {
    }

    public GuideRouteNote(UUID guideId, UUID trackId, String note) {
        this.guideId = guideId;
        this.trackId = trackId;
        this.note = note;
        this.updatedAt = Instant.now();
    }

    public UUID getGuideId() {
        return guideId;
    }

    public UUID getTrackId() {
        return trackId;
    }

    public String getNote() {
        return note;
    }

    public record Key(UUID guideId, UUID trackId) implements Serializable {
    }
}
