package com.sahyatri.blog.dto;

import com.sahyatri.blog.entity.BlogSchemaType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A whole post. {@code publishedAt} is null for a draft and {@code researchNotes} for the public (admin only).
 * {@code pendingLinks} are the /blog paths the body links to that have no public page yet; the page shows their words
 * as plain text until they do.
 */
public record BlogPostResponse(UUID id, String slug, String title, String seoTitle, String excerpt, String body,
                               List<String> pendingLinks, BlogCategoryRef category, UUID coverPhotoId,
                               String coverUrl, String coverCaption, String coverAlt, LocalDate coverTakenOn,
                               List<BlogPhotoResponse> photos, String authorName, List<BlogFaq> faqs, List<String> tags,
                               BlogSchemaType schemaType, List<BlogTrekRef> relatedTreks, String researchNotes,
                               Instant publishedAt, Instant updatedAt) {
}
