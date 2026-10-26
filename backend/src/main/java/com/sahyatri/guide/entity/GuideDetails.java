package com.sahyatri.guide.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A guide's credentials as trekkers see them. Home and bio stay on the account's profile. */
@Entity
@Table(name = "guide_profiles")
public class GuideDetails {

    @Id
    private UUID userId;

    /** Year they started leading treks; years leading is worked out when read. */
    private Integer leadingSince;

    /** E.g. "Hindi, Garhwali, English". */
    private String languages;

    /** Any other certificate (first aid, rescue); the two mountaineering courses have their own fields. */
    private String certification;

    private String certificationNumber;

    /** Basic Mountaineering Course: the institute that ran it, e.g. "Nehru Institute of Mountaineering, Uttarkashi". */
    private String bmcInstitute;

    private String bmcCertificateNumber;

    /** Advanced Mountaineering Course. */
    private String amcInstitute;

    private String amcCertificateNumber;

    /** In their own words, one or two lines. Shown as the intro under the guide's name. */
    private String quote;

    /** The profile's URL, /guides/{slug}. Old ones stay in guide_slug_history. */
    private String slug;

    /** Cover photo key in storage, see {@code GuideCoverFiles}. */
    private String coverKey;

    /** Treks led elsewhere in the last three years, from onboarding (admin-set, §7.21.8). */
    @Column(name = "treks_last_3y")
    private Integer treksLast3y;

    /** Admin-set: shows "Route verified" on the guides list whatever their treks led (§7.17). */
    @Column(nullable = false)
    private boolean routeVerified;

    /** Admin-set "Batches led"; null = worked out from their routes and departures (§7.17). */
    private Integer batchesLed;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected GuideDetails() {
    }

    public GuideDetails(UUID userId) {
        this.userId = userId;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public void update(Integer leadingSince, String languages, String certification, String certificationNumber,
                       String bmcInstitute, String bmcCertificateNumber, String amcInstitute,
                       String amcCertificateNumber, String quote, boolean routeVerified, Integer batchesLed) {
        this.leadingSince = leadingSince;
        this.languages = languages;
        this.certification = certification;
        this.certificationNumber = certificationNumber;
        this.bmcInstitute = bmcInstitute;
        this.bmcCertificateNumber = bmcCertificateNumber;
        this.amcInstitute = amcInstitute;
        this.amcCertificateNumber = amcCertificateNumber;
        this.quote = quote;
        this.routeVerified = routeVerified;
        this.batchesLed = batchesLed;
    }

    /** What the guide edits about themselves; the course fields stay admin-set. */
    public void updateOwn(Integer leadingSince, String languages, String quote) {
        this.leadingSince = leadingSince;
        this.languages = languages;
        this.quote = quote;
    }

    public void setTreksLast3y(Integer treksLast3y) {
        this.treksLast3y = treksLast3y;
    }

    public Integer getTreksLast3y() {
        return treksLast3y;
    }

    public UUID getUserId() {
        return userId;
    }

    public Integer getLeadingSince() {
        return leadingSince;
    }

    public String getLanguages() {
        return languages;
    }

    public String getCertification() {
        return certification;
    }

    public String getCertificationNumber() {
        return certificationNumber;
    }

    public String getBmcInstitute() {
        return bmcInstitute;
    }

    public String getBmcCertificateNumber() {
        return bmcCertificateNumber;
    }

    public String getAmcInstitute() {
        return amcInstitute;
    }

    public String getAmcCertificateNumber() {
        return amcCertificateNumber;
    }

    public boolean isRouteVerified() {
        return routeVerified;
    }

    public Integer getBatchesLed() {
        return batchesLed;
    }

    public String getQuote() {
        return quote;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getCoverKey() {
        return coverKey;
    }

    public void setCoverKey(String coverKey) {
        this.coverKey = coverKey;
    }
}
