package com.sahyatri.catalog.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A route. Departures are dated runs of a track. */
@Entity
@Table(name = "tracks")
public class Track {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    private int durationDays;

    @Column(name = "max_altitude_m")
    private Integer maxAltitudeM;

    @Column(nullable = false)
    private String summary;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String meetingPoint;

    /** On foot, start to finish. */
    private BigDecimal distanceKm;

    /** Where the walking starts; with {@code maxAltitudeM} it gives the altitude gain. */
    @Column(name = "base_altitude_m")
    private Integer baseAltitudeM;

    @Column(name = "highest_camp_m")
    private Integer highestCampM;

    /** E.g. "Tents · twin share". */
    private String stay;

    /** E.g. "Snow trek · Dec–Apr". */
    private String seasonLabel;

    /** Shown in the public catalog even with no upcoming dates. Tracks with dates are shown regardless. */
    @Column(nullable = false)
    private boolean listed;

    @OneToMany(mappedBy = "track", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayNumber")
    private List<TrackItineraryDay> itinerary = new ArrayList<>();

    /** Read-only here; {@code TrackPhotoService} adds and removes photos. */
    @OneToMany(mappedBy = "track")
    @OrderBy("createdAt, id")
    private List<TrackPhoto> photos = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Track() {
    }

    public static Track create() {
        Track track = new Track();
        track.id = UUID.randomUUID();
        track.createdAt = Instant.now();
        track.updatedAt = track.createdAt;
        return track;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public void update(String slug, String name, String region, Difficulty difficulty, int durationDays,
                       Integer maxAltitudeM, String summary, String description, String meetingPoint) {
        this.slug = slug;
        this.name = name;
        this.region = region;
        this.difficulty = difficulty;
        this.durationDays = durationDays;
        this.maxAltitudeM = maxAltitudeM;
        this.summary = summary;
        this.description = description;
        this.meetingPoint = meetingPoint;
    }

    public void updateRouteFacts(BigDecimal distanceKm, Integer baseAltitudeM, Integer highestCampM, String stay,
                                 String seasonLabel) {
        this.distanceKm = distanceKm;
        this.baseAltitudeM = baseAltitudeM;
        this.highestCampM = highestCampM;
        this.stay = stay;
        this.seasonLabel = seasonLabel;
    }

    public void setListed(boolean listed) {
        this.listed = listed;
    }

    /** Day 1 first. Flush between clearing and adding: Hibernate inserts before it deletes, and days are unique. */
    public void clearItinerary() {
        itinerary.clear();
    }

    public void addItineraryDay(String summary) {
        itinerary.add(new TrackItineraryDay(this, itinerary.size() + 1, summary));
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getRegion() {
        return region;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public int getDurationDays() {
        return durationDays;
    }

    public Integer getMaxAltitudeM() {
        return maxAltitudeM;
    }

    public String getSummary() {
        return summary;
    }

    public String getDescription() {
        return description;
    }

    public String getMeetingPoint() {
        return meetingPoint;
    }

    public BigDecimal getDistanceKm() {
        return distanceKm;
    }

    public Integer getBaseAltitudeM() {
        return baseAltitudeM;
    }

    public Integer getHighestCampM() {
        return highestCampM;
    }

    public String getStay() {
        return stay;
    }

    public String getSeasonLabel() {
        return seasonLabel;
    }

    public boolean isListed() {
        return listed;
    }

    public List<TrackItineraryDay> getItinerary() {
        return itinerary;
    }

    public List<TrackPhoto> getPhotos() {
        return photos;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
