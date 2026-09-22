package com.sahyatri.profile.dto;

import com.sahyatri.auth.dto.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmergencyContact(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 50) String relation,
        @NotBlank @Pattern(regexp = ValidationPatterns.INDIAN_MOBILE, message = ValidationPatterns.INDIAN_MOBILE_MESSAGE)
        String phone) {
}
