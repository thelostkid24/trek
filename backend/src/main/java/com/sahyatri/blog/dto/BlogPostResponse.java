package com.sahyatri.blog.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A whole post. {@code publishedAt} is null for a draft (admin only). */
public record BlogPostResponse(UUID id, String slug, String title, String excerpt, String body,
                               BlogCategoryRef category, UUID coverPhotoId, String coverUrl,
                               List<BlogPhotoResponse> photos, String authorName, Instant publishedAt,
                               Instant updatedAt) {
}
