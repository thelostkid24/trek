package com.sahyatri.blog.dto;

import java.util.UUID;

/** The category a post is filed under; {@code parent} is set for a sub-category. */
public record BlogCategoryRef(UUID id, String name, String slug, BlogCategoryRef parent) {
}
