package com.sahyatri.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * A sub-category under one of the ten categories ({@code parentId}, required on create), or an edit of any category's
 * name, slug and description (the parent never changes).
 */
public record BlogCategoryRequest(
        @NotBlank @Size(max = 60) String name,
        @NotBlank @Size(max = 80)
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "must be lowercase letters and digits separated by hyphens")
        String slug,
        @Size(max = 300) String description,
        UUID parentId) {
}
