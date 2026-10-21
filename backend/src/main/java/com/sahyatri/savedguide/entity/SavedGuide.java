package com.sahyatri.savedguide.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** A guide a trekker saved ("My Fav", §7.21.7). Private to the trekker; the guide isn't told. */
@Entity
@Table(name = "saved_guides")
@IdClass(SavedGuide.Key.class)
public class SavedGuide {

    @Id
    private UUID userId;

    @Id
    private UUID guideId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected SavedGuide() {
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getGuideId() {
        return guideId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public record Key(UUID userId, UUID guideId) implements Serializable {
    }
}
