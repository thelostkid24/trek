package com.sahyatri.question.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A visitor's question; {@code emailCode} is the code sent to {@code email} by {@code /api/public/questions/email-code}. */
public record VisitorQuestionRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 12) String emailCode,
        @NotBlank @Size(max = 2000) String body) {
}
