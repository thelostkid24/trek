package com.sahyatri.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One day of the itinerary: a heading ("Sankri to Juda ka Talab") and optional route details. Altitudes run start →
 * high point → end; the day's chart bar is the highest of high and end (the start is where you slept).
 */
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

    private String description;

    private BigDecimal distanceKm;

    @Column(name = "start_altitude_m")
    private Integer startAltitudeM;

    @Column(name = "high_altitude_m")
    private Integer highAltitudeM;

    @Column(name = "end_altitude_m")
    private Integer endAltitudeM;

    private BigDecimal hoursMin;

    private BigDecimal hoursMax;

    /** E.g. "mostly downhill". */
    private String routeNote;

    protected TrackItineraryDay() {
    }

    TrackItineraryDay(Track track, int dayNumber, String summary) {
        this.id = UUID.randomUUID();
        this.track = track;
        this.dayNumber = dayNumber;
        this.summary = summary;
    }

    public void setDetails(String description, BigDecimal distanceKm, Integer startAltitudeM, Integer highAltitudeM,
                           Integer endAltitudeM, BigDecimal hoursMin, BigDecimal hoursMax, String routeNote) {
        this.description = description;
        this.distanceKm = distanceKm;
        this.startAltitudeM = startAltitudeM;
        this.highAltitudeM = highAltitudeM;
        this.endAltitudeM = endAltitudeM;
        this.hoursMin = hoursMin;
        this.hoursMax = hoursMax;
        this.routeNote = routeNote;
    }

    public int getDayNumber() {
        return dayNumber;
    }

    public String getSummary() {
        return summary;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getDistanceKm() {
        return distanceKm;
    }

    public Integer getStartAltitudeM() {
        return startAltitudeM;
    }

    public Integer getHighAltitudeM() {
        return highAltitudeM;
    }

    public Integer getEndAltitudeM() {
        return endAltitudeM;
    }

    public BigDecimal getHoursMin() {
        return hoursMin;
    }

    public BigDecimal getHoursMax() {
        return hoursMax;
    }

    public String getRouteNote() {
        return routeNote;
    }
}
