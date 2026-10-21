package com.sahyatri.question.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A signed-in trekker's question. Their name comes from the account. */
public record AskQuestionRequest(@NotBlank @Size(max = 2000) String body) {
}
