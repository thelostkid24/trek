package com.sahyatri.account.dto;

import com.sahyatri.auth.dto.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** @param currentPassword required only when the account already has a password */
public record PasswordChangeRequest(
        @Size(max = 72) String currentPassword,
        @NotBlank @Pattern(regexp = ValidationPatterns.PASSWORD, message = ValidationPatterns.PASSWORD_MESSAGE)
        String newPassword) {
}
