package com.sahyatri.snow.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A weekly report. {@code photoUrl} is null until a photo is added. */
public record SnowReportResponse(
        UUID id,
        LocalDate reportedOn,
        String reportedFrom,
        Integer snowlineM,
        Integer nightTempC,
        List<Condition> conditions,
        String crowdPlace,
        Integer crowdTents,
        String note,
        String photoUrl,
        Reporter reportedBy,
        Instant createdAt) {

    public record Reporter(UUID id, String fullName) {
    }
}
