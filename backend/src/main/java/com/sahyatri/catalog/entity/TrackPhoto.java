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

    /** E.g. "Summit ridge at first light". */
    private String caption;

    /** E.g. "Kedarkantha summit". */
    private String place;

    /** Which itinerary day it was taken on. */
    private Integer dayNumber;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected TrackPhoto() {
    }

    public TrackPhoto(Track track, String caption, String place, Integer dayNumber) {
        this.id = UUID.randomUUID();
        this.track = track;
        this.createdAt = Instant.now();
        describe(caption, place, dayNumber);
    }

    public void describe(String caption, String place, Integer dayNumber) {
        this.caption = caption;
        this.place = place;
        this.dayNumber = dayNumber;
    }

    public UUID getId() {
        return id;
    }

    public String getCaption() {
        return caption;
    }

    public String getPlace() {
        return place;
    }

    public Integer getDayNumber() {
        return dayNumber;
    }
}
