package com.sahyatri.blog.dto;

import java.time.Instant;
import java.util.UUID;

/** A post in a list: no body or photos. */
public record BlogPostSummary(UUID id, String slug, String title, String excerpt, BlogCategoryRef category,
                              String coverUrl, Instant publishedAt, Instant updatedAt) {
}
