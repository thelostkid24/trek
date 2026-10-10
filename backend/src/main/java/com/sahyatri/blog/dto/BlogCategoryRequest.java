package com.sahyatri.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** A category, or a sub-category when {@code parentId} names a top-level one. */
public record BlogCategoryRequest(
        @NotBlank @Size(max = 60) String name,
        @NotBlank @Size(max = 80)
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "must be lowercase letters and digits separated by hyphens")
        String slug,
        UUID parentId) {
}
