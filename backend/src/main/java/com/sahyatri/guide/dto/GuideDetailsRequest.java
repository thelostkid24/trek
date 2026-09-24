package com.sahyatri.guide.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Replaces a guide's credentials. Blank text clears a field. */
public record GuideDetailsRequest(
        @Min(1950) @Max(2100) Integer leadingSince,
        @Size(max = 120) String languages,
        @Size(max = 160) String certification,
        @Size(max = 60) String certificationNumber,
        @Size(max = 240) String quote) {
}
