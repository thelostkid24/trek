package com.sahyatri.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The guide's public reply to a review. */
public record ReplyRequest(@NotBlank @Size(max = 1000) String body) {
}
