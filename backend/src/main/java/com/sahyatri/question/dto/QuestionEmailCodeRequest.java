package com.sahyatri.question.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The visitor's email to send a code to before their question goes out. */
public record QuestionEmailCodeRequest(@NotBlank @Email @Size(max = 254) String email) {
}
