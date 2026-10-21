package com.sahyatri.guide.dto;

import com.sahyatri.guide.entity.CertificationType;

import java.time.LocalDate;
import java.util.UUID;

/** A verified certificate on the public page. {@code number} only for BMC and AMC; never the document. */
public record PublicCertification(UUID id, CertificationType type, String title, String issuer, Integer year,
                                  LocalDate validTo, String number, CertificationStatus status) {
}
