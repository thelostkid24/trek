package com.sahyatri.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record BlogPostRequest(
        @NotBlank @Size(max = 150) String title,
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "must be lowercase letters and digits separated by hyphens")
        String slug,
        @NotNull UUID categoryId,
        @Size(max = 300) String excerpt,
        @NotBlank @Size(max = 50000) String body) {
}
