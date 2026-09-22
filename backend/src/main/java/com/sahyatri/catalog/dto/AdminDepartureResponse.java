package com.sahyatri.catalog.dto;

import com.sahyatri.catalog.entity.CancelReason;
import com.sahyatri.catalog.entity.DepartureStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminDepartureResponse(
        UUID id,
        TrackRef track,
        GuideRef guide,
        LocalDate startDate,
        LocalDate endDate,
        long pricePaise,
        int maxGroupSize,
        int seatsTaken,
        DepartureStatus status,
        Integer guideShareBps,
        Instant publishedAt,
        Instant cancelledAt,
        CancelReason cancelReasonCode,
        String cancelReasonNote,
        Instant createdAt,
        Instant updatedAt) {

    public record TrackRef(UUID id, String slug, String name, int durationDays) {
    }

    public record GuideRef(UUID id, String fullName, String email, String avatarUrl) {
    }
}
