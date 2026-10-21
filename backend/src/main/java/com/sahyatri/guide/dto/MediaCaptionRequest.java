package com.sahyatri.guide.dto;

import jakarta.validation.constraints.Size;

/** Blank clears the caption. */
public record MediaCaptionRequest(@Size(max = 120) String caption) {
}
