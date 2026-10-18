package com.sahyatri.blog.dto;

import com.sahyatri.blog.entity.BlogSchemaType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** One post in an import file: a draft's fields, with its sub-category and treks named by slug (ids differ per site). */
public record BlogImportPost(
        @NotBlank @Size(max = 150) String title,
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "must be lowercase letters and digits separated by hyphens")
        String slug,
        @NotBlank String categorySlug,
        @Size(max = 70) String seoTitle,
        @Size(max = 160) String excerpt,
        @NotBlank @Size(max = 50000) String body,
        @Size(max = 200) String coverCaption,
        @Size(max = 200) String coverAlt,
        @Size(max = 100) String authorName,
        @Size(max = 20) List<@Valid BlogFaq> faqs,
        @Size(max = 6) List<@NotBlank @Size(max = 40) String> tags,
        BlogSchemaType schemaType,
        @Size(max = 20000) String researchNotes,
        @Size(max = 20) List<@NotBlank String> trackSlugs) {
}
