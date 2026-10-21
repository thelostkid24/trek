package com.sahyatri.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Why a guide reports a review, or why an admin hides one. */
public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {
}
