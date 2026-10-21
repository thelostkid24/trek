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
import java.util.Objects;
import java.util.UUID;

/**
 * One course or certificate a guide holds (§7.21.3). The guide enters it; an admin verifies it against the original
 * document. Any change by the guide drops the verification.
 */
@Entity
@Table(name = "guide_certifications")
public class GuideCertification {

    @Id
    private UUID id;

    private UUID guideId;

    @Enumerated(EnumType.STRING)
    private CertificationType type;

    /** Required for OTHER; otherwise overrides the type's name. */
    private String title;

    /** E.g. "Nehru Institute of Mountaineering, Uttarkashi". */
    private String issuer;

    private Integer year;

    /** Last day it's valid (first-responder courses, insurance); "Expired" after it. */
    private LocalDate validTo;

    private String number;

    /** Private file in storage, see {@code GuideCertificateFiles}. */
    private String documentKey;

    private Instant verifiedAt;

    /** The admin who verified it; null for rows copied from §7.12 by V26. */
    private UUID verifiedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected GuideCertification() {
    }

    public GuideCertification(UUID guideId) {
        this.id = UUID.randomUUID();
        this.guideId = guideId;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    /** Sets the details; returns whether anything changed (and if so, verification is dropped). */
    public boolean edit(CertificationType type, String title, String issuer, Integer year, LocalDate validTo,
                        String number) {
        boolean changed = this.type != type || !Objects.equals(this.title, title)
                || !Objects.equals(this.issuer, issuer) || !Objects.equals(this.year, year)
                || !Objects.equals(this.validTo, validTo) || !Objects.equals(this.number, number);
        this.type = type;
        this.title = title;
        this.issuer = issuer;
        this.year = year;
        this.validTo = validTo;
        this.number = number;
        if (changed) {
            unverify();
        }
        return changed;
    }

    /** A new document always drops verification: the admin has to look at it. */
    public void replaceDocument(String documentKey) {
        this.documentKey = documentKey;
        unverify();
    }

    public void verify(UUID adminId) {
        this.verifiedAt = Instant.now();
        this.verifiedBy = adminId;
    }

    public void unverify() {
        this.verifiedAt = null;
        this.verifiedBy = null;
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    public boolean isExpired(LocalDate today) {
        return validTo != null && validTo.isBefore(today);
    }

    public UUID getId() {
        return id;
    }

    public UUID getGuideId() {
        return guideId;
    }

    public CertificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getIssuer() {
        return issuer;
    }

    public Integer getYear() {
        return year;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public String getNumber() {
        return number;
    }

    public String getDocumentKey() {
        return documentKey;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public UUID getVerifiedBy() {
        return verifiedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
