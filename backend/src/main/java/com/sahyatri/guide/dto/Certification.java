package com.sahyatri.guide.dto;

import com.sahyatri.guide.entity.CertificationType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A certificate as its guide or an admin sees it. {@code documentUrl} is an API path that needs the caller's token
 * (the guide's own or the admin one), or null with no document.
 */
public record Certification(UUID id, CertificationType type, String title, String issuer, Integer year,
                            LocalDate validTo, String number, CertificationStatus status, String documentUrl,
                            Instant verifiedAt, Instant createdAt, Instant updatedAt) {
}
