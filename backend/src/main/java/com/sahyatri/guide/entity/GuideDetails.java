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

    private String certification;

    private String certificationNumber;

    /** In their own words, one or two lines. */
    private String quote;

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
                       String quote) {
        this.leadingSince = leadingSince;
        this.languages = languages;
        this.certification = certification;
        this.certificationNumber = certificationNumber;
        this.quote = quote;
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

    public String getQuote() {
        return quote;
    }
}
