package com.sahyatri.guide.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Replaces a guide's credentials. Blank text clears a field. How often they led each trek before Sahyātri is in their
 * record (§7.21.8). {@code routeVerified}: show "Route verified" on the guides list whatever their treks led (§7.17);
 * left out, it's false.
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
        Boolean routeVerified) {
}
