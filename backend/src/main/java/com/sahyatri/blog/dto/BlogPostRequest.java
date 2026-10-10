package com.sahyatri.blog.dto;

import com.sahyatri.blog.entity.BlogSchemaType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Everything the editor saves. A draft may leave the hero caption and excerpt blank; publishing needs them. */
public record BlogPostRequest(
        @NotBlank @Size(max = 150) String title,
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "must be lowercase letters and digits separated by hyphens")
        String slug,
        @NotNull UUID categoryId,
        @Size(max = 160) String excerpt,
        @NotBlank @Size(max = 50000) String body,
        @Size(max = 200) String coverCaption,
        LocalDate coverTakenOn,
        @Size(max = 100) String authorName,
        @Size(max = 20) List<@Valid BlogFaq> faqs,
        BlogSchemaType schemaType,
        @Size(max = 20000) String researchNotes,
        @Size(max = 20) List<@NotNull UUID> trackIds) {
}
