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
                       String bmcInstitute, String bmcCertificateNumber, String amcInstitute,
                       String amcCertificateNumber, String quote) {
        this.leadingSince = leadingSince;
        this.languages = languages;
        this.certification = certification;
        this.certificationNumber = certificationNumber;
        this.bmcInstitute = bmcInstitute;
        this.bmcCertificateNumber = bmcCertificateNumber;
        this.amcInstitute = amcInstitute;
        this.amcCertificateNumber = amcCertificateNumber;
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

    public String getQuote() {
        return quote;
    }
}
