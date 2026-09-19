package com.sahyatri.catalog.dto;

import com.sahyatri.catalog.entity.CancelReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Force-majeure cancellation (law 4): the reason is mandatory. */
public record CancelDepartureRequest(
        @NotNull CancelReason reasonCode,
        @NotBlank @Size(max = 1000) String reasonNote) {
}
