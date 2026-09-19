package com.sahyatri.booking.dto;

import com.sahyatri.auth.dto.ValidationPatterns;
import com.sahyatri.catalog.entity.Departure;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * A signed-in trekker's hold. Contact fields left out fall back to the account (checked in the service).
 * Travellers are optional here and can be added after payment; when given, the count must equal {@code seats}.
 */
public record BookingRequest(
        @NotNull UUID departureId,
        @NotNull @Min(1) @Max(Departure.MAX_GROUP_SIZE) Integer seats,
        @Size(max = 100) String fullName,
        @Pattern(regexp = ValidationPatterns.INDIAN_MOBILE, message = ValidationPatterns.INDIAN_MOBILE_MESSAGE)
        String phone,
        @Email @Size(max = 254) String email,
        @Size(max = Departure.MAX_GROUP_SIZE) List<@Valid @NotNull TravellerRequest> travellers) {
}
