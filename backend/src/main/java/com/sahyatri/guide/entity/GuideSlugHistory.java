package com.sahyatri.guide.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A slug a guide used before. Links with it redirect to the current one; no other guide may take it. */
@Entity
@Table(name = "guide_slug_history")
public class GuideSlugHistory {

    @Id
    private String slug;

    private UUID guideId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected GuideSlugHistory() {
    }

    public GuideSlugHistory(String slug, UUID guideId) {
        this.slug = slug;
        this.guideId = guideId;
        this.createdAt = Instant.now();
    }

    public String getSlug() {
        return slug;
    }

    public UUID getGuideId() {
        return guideId;
    }
}
