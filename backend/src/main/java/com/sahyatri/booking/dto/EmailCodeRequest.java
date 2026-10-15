package com.sahyatri.booking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The booking contact email to send a checkout code to. */
public record EmailCodeRequest(@NotBlank @Email @Size(max = 254) String email) {
}
