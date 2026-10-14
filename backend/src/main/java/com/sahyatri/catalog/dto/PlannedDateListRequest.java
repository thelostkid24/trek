package com.sahyatri.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Replaces a track's planned dates. Empty clears them. */
public record PlannedDateListRequest(@NotNull @Size(max = 40) List<@Valid @NotNull PlannedDateRequest> items) {
}
