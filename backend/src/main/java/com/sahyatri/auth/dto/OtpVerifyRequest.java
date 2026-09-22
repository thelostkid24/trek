package com.sahyatri.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OtpVerifyRequest(
        @NotBlank @Pattern(regexp = ValidationPatterns.INDIAN_MOBILE, message = ValidationPatterns.INDIAN_MOBILE_MESSAGE)
        String phone,
        @NotBlank @Pattern(regexp = ValidationPatterns.OTP_CODE, message = "must be 6 digits") String code,
        @Size(min = 1, max = 100) String fullName) {
}
