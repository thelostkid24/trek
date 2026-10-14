package com.sahyatri.catalog.dto;

import java.time.LocalDate;

/** A planned date as shown: the end follows from the trek's length; {@code note} is e.g. "Christmas batch". */
public record PlannedDate(LocalDate startDate, LocalDate endDate, String note) {
}
