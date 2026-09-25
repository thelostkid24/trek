package com.sahyatri.booking.dto;

import com.sahyatri.auth.dto.ValidationPatterns;
import com.sahyatri.catalog.entity.Departure;
import com.sahyatri.common.acquisition.AcquisitionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Guest checkout: the three fields, no password or OTP. {@code phone} is the WhatsApp number. */
public record GuestBookingRequest(
        @NotNull UUID departureId,
        @NotNull @Min(1) @Max(Departure.MAX_GROUP_SIZE) Integer seats,
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Pattern(regexp = ValidationPatterns.INDIAN_MOBILE, message = ValidationPatterns.INDIAN_MOBILE_MESSAGE)
        String phone,
        @NotBlank @Email @Size(max = 254) String email,
        @Valid AcquisitionRequest acquisition) {
}
