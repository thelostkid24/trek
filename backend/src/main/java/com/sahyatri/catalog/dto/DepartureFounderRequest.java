package com.sahyatri.catalog.dto;

import jakarta.validation.constraints.NotNull;

/** Whether the founder co-leads this departure's batch. */
public record DepartureFounderRequest(@NotNull Boolean founderCoLeads) {
}
