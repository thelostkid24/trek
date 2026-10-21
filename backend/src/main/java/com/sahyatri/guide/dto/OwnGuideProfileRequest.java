package com.sahyatri.guide.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Replaces what the guide edits about themselves. Blank optional text clears it; null lists clear them. The course
 * credentials, slug and prior treks stay admin-set.
 */
public record OwnGuideProfileRequest(
        @NotBlank @Size(max = 100) String fullName,
        @Size(max = 100) String homeCity,
        @Size(max = 2000) String bio,
        @Size(max = 240) String quote,
        @Size(max = 120) String languages,
        @Min(1950) @Max(2100) Integer leadingSince,
        @Size(max = 8) List<@NotNull UUID> specialtyIds,
        @Size(max = 30) List<@NotNull @Valid Milestone> milestones,
        @Size(max = 50) List<@NotNull @Valid RouteNote> routeNotes) {
}
