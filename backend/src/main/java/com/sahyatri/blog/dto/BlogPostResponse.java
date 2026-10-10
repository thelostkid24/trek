package com.sahyatri.blog.dto;

import com.sahyatri.blog.entity.BlogSchemaType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A whole post. {@code publishedAt} is null for a draft and {@code researchNotes} for the public (admin only). */
public record BlogPostResponse(UUID id, String slug, String title, String excerpt, String body,
                               BlogCategoryRef category, UUID coverPhotoId, String coverUrl, String coverCaption,
                               LocalDate coverTakenOn, List<BlogPhotoResponse> photos, String authorName,
                               List<BlogFaq> faqs, BlogSchemaType schemaType, List<BlogTrekRef> relatedTreks,
                               String researchNotes, Instant publishedAt, Instant updatedAt) {
}
