package com.sahyatri.guide.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** How often a guide led a trek before Sahyātri. Replaced as a set from the credentials form. */
@Entity
@Table(name = "guide_prior_treks")
@IdClass(GuidePriorTrek.Key.class)
public class GuidePriorTrek {

    @Id
    private UUID guideId;

    @Id
    private UUID trackId;

    private int times;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected GuidePriorTrek() {
    }

    public GuidePriorTrek(UUID guideId, UUID trackId, int times) {
        this.guideId = guideId;
        this.trackId = trackId;
        this.times = times;
        this.createdAt = Instant.now();
    }

    public UUID getGuideId() {
        return guideId;
    }

    public UUID getTrackId() {
        return trackId;
    }

    public int getTimes() {
        return times;
    }

    public record Key(UUID guideId, UUID trackId) implements Serializable {
    }
}
