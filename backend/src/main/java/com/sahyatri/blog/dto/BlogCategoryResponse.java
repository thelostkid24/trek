package com.sahyatri.blog.dto;

import java.util.UUID;

/** A category with how many published posts are filed directly under it. */
public record BlogCategoryResponse(UUID id, UUID parentId, String name, String slug, long publishedPosts) {
}
