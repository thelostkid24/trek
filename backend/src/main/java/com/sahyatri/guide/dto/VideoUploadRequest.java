package com.sahyatri.guide.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Starts a video upload. {@code duration_s} is read by the browser from the file; the size and type are checked
 * again against what arrives in storage.
 */
public record VideoUploadRequest(@NotBlank String contentType, @NotNull @Positive Long sizeBytes,
                                 @NotNull @Positive Double durationS, @Size(max = 120) String caption) {
}
