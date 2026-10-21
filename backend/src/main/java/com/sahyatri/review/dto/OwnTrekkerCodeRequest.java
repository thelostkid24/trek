package com.sahyatri.review.dto;

import com.sahyatri.auth.dto.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Sends a one-time code to the email or phone an off-platform reviewer will confirm. */
public record OwnTrekkerCodeRequest(
        @NotNull ReviewChannel channel,
        @Email @Size(max = 254) String email,
        @Pattern(regexp = ValidationPatterns.INDIAN_MOBILE, message = ValidationPatterns.INDIAN_MOBILE_MESSAGE)
        String phone) {
}
