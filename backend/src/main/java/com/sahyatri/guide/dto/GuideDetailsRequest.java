package com.sahyatri.guide.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Replaces a guide's credentials. Blank text clears a field. {@code priorTreks} replaces how often they led each trek
 * before Sahyātri; null leaves those as they are.
 */
public record GuideDetailsRequest(
        @Min(1950) @Max(2100) Integer leadingSince,
        @Size(max = 120) String languages,
        @Size(max = 160) String certification,
        @Size(max = 60) String certificationNumber,
        @Size(max = 160) String bmcInstitute,
        @Size(max = 60) String bmcCertificateNumber,
        @Size(max = 160) String amcInstitute,
        @Size(max = 60) String amcCertificateNumber,
        @Size(max = 240) String quote,
        @Size(max = 100) List<@Valid @NotNull PriorTrek> priorTreks) {
}
