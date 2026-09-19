package com.sahyatri.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpRequest(
        @NotBlank @Pattern(regexp = ValidationPatterns.INDIAN_MOBILE, message = ValidationPatterns.INDIAN_MOBILE_MESSAGE)
        String phone) {
}
