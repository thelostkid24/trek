package com.sahyatri.snow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One labelled reading, e.g. "Road, Purola to Sankri": "Open". */
public record Condition(@NotBlank @Size(max = 40) String label, @NotBlank @Size(max = 60) String value) {
}
