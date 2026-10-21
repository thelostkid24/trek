package com.sahyatri.guide.dto;

import com.sahyatri.guide.entity.CertificationType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** A guide adds or rewrites one certificate. Blank text clears a field; {@code title} is required for OTHER. */
public record CertificationRequest(
        @NotNull CertificationType type,
        @Size(max = 120) String title,
        @Size(max = 160) String issuer,
        @Min(1950) @Max(2100) Integer year,
        LocalDate validTo,
        @Size(max = 60) String number) {
}
