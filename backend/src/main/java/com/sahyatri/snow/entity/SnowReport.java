package com.sahyatri.snow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One week's conditions on a trek, from the trailhead. Kept forever; the newest is shown on the trek page. */
@Entity
@Table(name = "snow_reports")
public class SnowReport {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID trackId;

    @Column(nullable = false)
    private UUID reportedBy;

    @Column(nullable = false)
    private LocalDate reportedOn;

    /** E.g. "Sankri". */
    @Column(nullable = false)
    private String reportedFrom;

    @Column(name = "snowline_m")
    private Integer snowlineM;

    @Column(name = "night_temp_c")
    private Integer nightTempC;

    /** JSON array of {label, value}, e.g. Juda ka Talab: Frozen. */
    @Column(columnDefinition = "jsonb", nullable = false)
    @ColumnTransformer(write = "?::jsonb")
    private String conditions;

    /** Where the tents were counted, e.g. "Juda ka Talab". Set together with {@code crowdTents}. */
    private String crowdPlace;

    private Integer crowdTents;

    private String note;

    private boolean hasPhoto;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected SnowReport() {
    }

    public SnowReport(UUID trackId, UUID reportedBy, LocalDate reportedOn, String reportedFrom, Integer snowlineM,
                      Integer nightTempC, String conditions, String crowdPlace, Integer crowdTents, String note) {
        this.id = UUID.randomUUID();
        this.trackId = trackId;
        this.reportedBy = reportedBy;
        this.reportedOn = reportedOn;
        this.reportedFrom = reportedFrom;
        this.snowlineM = snowlineM;
        this.nightTempC = nightTempC;
        this.conditions = conditions;
        this.crowdPlace = crowdPlace;
        this.crowdTents = crowdTents;
        this.note = note;
        this.createdAt = Instant.now();
    }

    public void photoAdded() {
        this.hasPhoto = true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTrackId() {
        return trackId;
    }

    public UUID getReportedBy() {
        return reportedBy;
    }

    public LocalDate getReportedOn() {
        return reportedOn;
    }

    public String getReportedFrom() {
        return reportedFrom;
    }

    public Integer getSnowlineM() {
        return snowlineM;
    }

    public Integer getNightTempC() {
        return nightTempC;
    }

    public String getConditions() {
        return conditions;
    }

    public String getCrowdPlace() {
        return crowdPlace;
    }

    public Integer getCrowdTents() {
        return crowdTents;
    }

    public String getNote() {
        return note;
    }

    public boolean hasPhoto() {
        return hasPhoto;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
