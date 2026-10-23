package com.sahyatri.guide.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A route a guide leads (§7.21.8): a trek in our catalog ({@code trackId}) or one we don't list ({@code name}). Admins
 * set the record from before Sahyātri (times led, last led, most recent, kind); the guide sets when they can lead it
 * and their note.
 */
@Entity
@Table(name = "guide_routes")
public class GuideRoute {

    @Id
    private UUID id;

    private UUID guideId;

    private UUID trackId;

    private String name;

    @Enumerated(EnumType.STRING)
    private GuideRouteKind kind;

    /** Times led before Sahyātri; null shows "Led". Completed departures with us are added on read. */
    private Integer timesLed;

    private Integer lastLedYear;

    private Integer lastLedMonth;

    /** The guide's most recent trek or climb: "Last led" reads "Most recent trek" when no date is known. */
    private boolean mostRecent;

    @Enumerated(EnumType.STRING)
    private RouteAvailability availability;

    private Integer seasonFromMonth;

    private Integer seasonToMonth;

    private LocalDate availableFrom;

    private String note;

    private int position;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected GuideRoute() {
    }

    public GuideRoute(UUID guideId, UUID trackId, String name, int position) {
        this.id = UUID.randomUUID();
        this.guideId = guideId;
        this.trackId = trackId;
        this.name = name;
        this.kind = GuideRouteKind.TREK;
        this.availability = RouteAvailability.ASK;
        this.position = position;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    /** The admin's part: which route, its kind and the record from before Sahyātri. */
    public void setRecord(UUID trackId, String name, GuideRouteKind kind, Integer timesLed, Integer lastLedYear,
                          Integer lastLedMonth, boolean mostRecent, int position) {
        this.trackId = trackId;
        this.name = name;
        this.kind = kind;
        this.timesLed = timesLed;
        this.lastLedYear = lastLedYear;
        this.lastLedMonth = lastLedMonth;
        this.mostRecent = mostRecent;
        this.position = position;
    }

    /** The guide's part (admins can set it too): when they can lead it, and their note. */
    public void setPlan(RouteAvailability availability, Integer seasonFromMonth, Integer seasonToMonth,
                        LocalDate availableFrom, String note) {
        this.availability = availability;
        this.seasonFromMonth = seasonFromMonth;
        this.seasonToMonth = seasonToMonth;
        this.availableFrom = availableFrom;
        this.note = note;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGuideId() {
        return guideId;
    }

    public UUID getTrackId() {
        return trackId;
    }

    public String getName() {
        return name;
    }

    public GuideRouteKind getKind() {
        return kind;
    }

    public Integer getTimesLed() {
        return timesLed;
    }

    public Integer getLastLedYear() {
        return lastLedYear;
    }

    public Integer getLastLedMonth() {
        return lastLedMonth;
    }

    public boolean isMostRecent() {
        return mostRecent;
    }

    public RouteAvailability getAvailability() {
        return availability;
    }

    public Integer getSeasonFromMonth() {
        return seasonFromMonth;
    }

    public Integer getSeasonToMonth() {
        return seasonToMonth;
    }

    public LocalDate getAvailableFrom() {
        return availableFrom;
    }

    public String getNote() {
        return note;
    }

    public int getPosition() {
        return position;
    }
}
