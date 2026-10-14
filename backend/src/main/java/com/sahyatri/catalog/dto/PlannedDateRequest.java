package com.sahyatri.catalog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** One planned date. Text is trimmed in the service; a blank note is none. */
public record PlannedDateRequest(@NotNull LocalDate startDate, @Size(max = 60) String note) {
}
