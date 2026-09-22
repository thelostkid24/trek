package com.sahyatri.booking.dto;

import com.sahyatri.auth.dto.ValidationPatterns;
import com.sahyatri.profile.entity.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Age (18–100 on the start date) is checked in the service. */
public record TravellerRequest(
        @NotBlank @Size(max = 100) String fullName,
        @Pattern(regexp = ValidationPatterns.INDIAN_MOBILE, message = ValidationPatterns.INDIAN_MOBILE_MESSAGE)
        String phone,
        @NotNull LocalDate dateOfBirth,
        @NotNull Gender gender) {
}
