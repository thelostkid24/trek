package com.sahyatri.guide.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A line in "Career in the mountains". The date is as exact as the guide knows it: none ("—"), a year, "~" a year
 * ({@code approximate}), a month or a day. The service rejects a month without a year, a day without a month, an
 * impossible day and a year after this one.
 */
public record Milestone(@Min(1950) @Max(2100) Integer year, @Min(1) @Max(12) Integer month,
                       @Min(1) @Max(31) Integer day, Boolean approximate,
                       @NotBlank @Size(max = 140) String text) {

    /** Left out means not approximate. */
    public Milestone {
        approximate = Boolean.TRUE.equals(approximate);
    }
}
