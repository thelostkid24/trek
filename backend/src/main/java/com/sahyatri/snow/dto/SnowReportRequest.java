package com.sahyatri.snow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** A new weekly report. Readings are optional; a tent count needs the place it was counted. Altitudes in metres. */
public record SnowReportRequest(
        @NotNull LocalDate reportedOn,
        @NotBlank @Size(max = 60) String reportedFrom,
        @Min(1) @Max(9000) Integer snowlineM,
        @Min(-60) @Max(50) Integer nightTempC,
        @Size(max = 4) List<@Valid @NotNull Condition> conditions,
        @Size(max = 60) String crowdPlace,
        @Min(0) @Max(2000) Integer crowdTents,
        @Size(max = 500) String note) {
}
