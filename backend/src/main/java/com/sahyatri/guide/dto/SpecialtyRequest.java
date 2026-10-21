package com.sahyatri.guide.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A new specialty for the list guides pick from. */
public record SpecialtyRequest(@NotBlank @Size(max = 40) String name) {
}
