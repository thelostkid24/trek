package com.sahyatri.snow.dto;

import java.time.LocalDate;

/** Tents counted at a camp on a report day, for the trek page's crowd history. */
public record CrowdCount(LocalDate reportedOn, String place, int tents) {
}
