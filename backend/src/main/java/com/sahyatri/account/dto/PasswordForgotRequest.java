package com.sahyatri.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordForgotRequest(@NotBlank @Email @Size(max = 254) String email) {
}
