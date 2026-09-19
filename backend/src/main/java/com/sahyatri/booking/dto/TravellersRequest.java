package com.sahyatri.booking.dto;

import com.sahyatri.catalog.entity.Departure;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Replaces a booking's travellers. The count must equal the booking's seats (checked in the service). */
public record TravellersRequest(
        @NotNull @Size(min = 1, max = Departure.MAX_GROUP_SIZE) List<@Valid @NotNull TravellerRequest> travellers) {
}
