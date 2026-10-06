package com.sahyatri.catalog.dto;

import com.sahyatri.catalog.entity.PhotoLicence;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Edits a photo's words: "Summit ridge at first light" · "Kedarkantha summit" · day 4 · by "Rohan Negi", ours.
 * Replaces them all; blank or omitted clears.
 */
public record TrackPhotoRequest(
        @Size(max = 200) String caption,
        @Size(max = 100) String place,
        @Min(1) @Max(7) Integer dayNumber,
        @Size(max = 100) String credit,
        PhotoLicence licence) {
}
