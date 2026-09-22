package com.sahyatri.catalog.dto;

import jakarta.validation.constraints.NotNull;

/** Show or hide a track in the public catalog while it has no upcoming dates. */
public record TrackListingRequest(@NotNull Boolean listed) {
}
