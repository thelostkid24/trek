package com.sahyatri.guide.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What trekkers read about a guide, set by an admin. Blank home city or bio clears it. */
public record GuideProfileRequest(
        @NotBlank @Size(max = 100) String fullName,
        @Size(max = 100) String homeCity,
        @Size(max = 2000) String bio) {
}
