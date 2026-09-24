package com.sahyatri.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One line of a trek-page list. {@code trackId} null = shown on every trek. Lists are replaced whole. */
@Entity
@Table(name = "trek_content_items")
public class TrekContentItem {

    @Id
    private UUID id;

    private UUID trackId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentKind kind;

    private int position;

    private String badge;

    private String title;

    @Column(nullable = false)
    private String body;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected TrekContentItem() {
    }

    public TrekContentItem(UUID trackId, ContentKind kind, int position, String badge, String title, String body) {
        this.id = UUID.randomUUID();
        this.trackId = trackId;
        this.kind = kind;
        this.position = position;
        this.badge = badge;
        this.title = title;
        this.body = body;
        this.createdAt = Instant.now();
    }

    public UUID getTrackId() {
        return trackId;
    }

    public ContentKind getKind() {
        return kind;
    }

    public int getPosition() {
        return position;
    }

    public String getBadge() {
        return badge;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }
}
