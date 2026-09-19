package com.sahyatri.booking.dto;

import com.sahyatri.catalog.dto.GuideBrief;
import com.sahyatri.catalog.dto.TrackBrief;
import com.sahyatri.catalog.entity.CancelReason;
import com.sahyatri.catalog.entity.DepartureStatus;

import java.time.LocalDate;
import java.util.UUID;

public record BookingDepartureRef(
        UUID id,
        DepartureStatus status,
        LocalDate startDate,
        LocalDate endDate,
        String meetingPoint,
        CancelReason cancelReasonCode,
        String cancelReasonNote,
        TrackBrief track,
        GuideBrief guide) {
}
