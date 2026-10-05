package com.sahyatri.booking.dto;

import com.sahyatri.auth.dto.ValidationPatterns;
import com.sahyatri.profile.entity.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Age (12–100 on the start date; under 18 only with an adult on the same booking) is checked in the service. Add-ons are per traveller: {@code insurance} takes ours,
 * {@code insuranceId} is their own policy instead (one or the other); left out = not taken.
 */
public record TravellerRequest(
        @NotBlank @Size(max = 100) String fullName,
        @Pattern(regexp = ValidationPatterns.INDIAN_MOBILE, message = ValidationPatterns.INDIAN_MOBILE_MESSAGE)
        String phone,
        @NotNull LocalDate dateOfBirth,
        @NotNull Gender gender,
        Boolean insurance,
        @Size(max = 60) String insuranceId,
        Boolean offloading,
        Boolean transport) {
}
