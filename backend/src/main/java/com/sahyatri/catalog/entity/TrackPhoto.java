package com.sahyatri.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A photo from a past run of the trek. The id is also its storage key. */
@Entity
@Table(name = "track_photos")
public class TrackPhoto {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "track_id")
    private Track track;

    /** E.g. "Summit push at first light". */
    private String caption;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected TrackPhoto() {
    }

    public TrackPhoto(Track track, String caption) {
        this.id = UUID.randomUUID();
        this.track = track;
        this.caption = caption;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getCaption() {
        return caption;
    }
}
