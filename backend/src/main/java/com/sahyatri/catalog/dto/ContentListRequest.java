package com.sahyatri.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Replaces one list, in order. Empty clears it. */
public record ContentListRequest(@NotNull @Size(max = 40) List<@Valid @NotNull ContentItem> items) {
}
