package com.sahyatri.guide.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** A safety pledge an admin recorded for a guide (§7.21.3). Replaced as a set from the admin's record form. */
@Entity
@Table(name = "guide_pledges")
@IdClass(GuidePledge.Key.class)
public class GuidePledge {

    @Id
    private UUID guideId;

    @Id
    @Enumerated(EnumType.STRING)
    private PledgeKind kind;

    @Column(nullable = false, updatable = false)
    private Instant recordedAt;

    private UUID recordedBy;

    protected GuidePledge() {
    }

    public GuidePledge(UUID guideId, PledgeKind kind, UUID recordedBy) {
        this.guideId = guideId;
        this.kind = kind;
        this.recordedBy = recordedBy;
        this.recordedAt = Instant.now();
    }

    public UUID getGuideId() {
        return guideId;
    }

    public PledgeKind getKind() {
        return kind;
    }

    public record Key(UUID guideId, PledgeKind kind) implements Serializable {
    }
}
