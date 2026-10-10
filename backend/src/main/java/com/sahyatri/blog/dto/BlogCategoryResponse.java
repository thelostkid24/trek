package com.sahyatri.blog.dto;

import java.util.UUID;

/** A category in the admin's flat list, with the published posts filed directly under it. */
public record BlogCategoryResponse(UUID id, UUID parentId, String name, String slug, String description, int position,
                                   long publishedPosts) {
}
