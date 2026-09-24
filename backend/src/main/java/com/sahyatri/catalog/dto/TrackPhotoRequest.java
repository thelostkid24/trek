package com.sahyatri.catalog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Edits a photo's words: "Summit ridge at first light" · "Kedarkantha summit" · day 4. Blank clears. */
public record TrackPhotoRequest(
        @Size(max = 200) String caption,
        @Size(max = 100) String place,
        @Min(1) @Max(7) Integer dayNumber) {
}
