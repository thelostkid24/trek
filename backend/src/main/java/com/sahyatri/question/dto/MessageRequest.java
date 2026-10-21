package com.sahyatri.question.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A reply in a question thread. */
public record MessageRequest(@NotBlank @Size(max = 2000) String body) {
}
