package com.sahyatri.account.dto;

import com.sahyatri.auth.dto.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PhoneVerifyRequest(
        @NotBlank @Pattern(regexp = ValidationPatterns.INDIAN_MOBILE, message = ValidationPatterns.INDIAN_MOBILE_MESSAGE)
        String phone,
        @NotBlank @Pattern(regexp = ValidationPatterns.OTP_CODE, message = "must be 6 digits") String code) {
}
