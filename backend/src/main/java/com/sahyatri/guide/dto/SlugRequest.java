package com.sahyatri.guide.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A guide's new profile URL slug, set by an admin. */
public record SlugRequest(
        @NotBlank @Size(max = 60)
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "use lower-case letters, digits and single hyphens")
        String slug) {
}
