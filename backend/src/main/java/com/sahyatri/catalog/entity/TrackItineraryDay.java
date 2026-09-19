package com.sahyatri.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/** One line of the itinerary, e.g. day 2 "Sankri to Juda ka Talab, 9,100 ft". */
@Entity
@Table(name = "track_itinerary_days")
public class TrackItineraryDay {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "track_id")
    private Track track;

    private int dayNumber;

    @Column(nullable = false)
    private String summary;

    protected TrackItineraryDay() {
    }

    TrackItineraryDay(Track track, int dayNumber, String summary) {
        this.id = UUID.randomUUID();
        this.track = track;
        this.dayNumber = dayNumber;
        this.summary = summary;
    }

    public int getDayNumber() {
        return dayNumber;
    }

    public String getSummary() {
        return summary;
    }
}
