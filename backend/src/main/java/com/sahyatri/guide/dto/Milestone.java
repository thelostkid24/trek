package com.sahyatri.guide.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A line in "Career in the mountains". The service rejects a year after this one. */
public record Milestone(@Min(1950) @Max(2100) int year, @NotBlank @Size(max = 140) String text) {
}
