package com.sahyatri.blog.dto;

import java.util.List;
import java.util.UUID;

/**
 * A category for the public blog with its published posts (a top-level one counts its sub-categories'). It's in the
 * menu (and indexed) at {@code BlogPublicService.MENU_MIN_POSTS}+ posts.
 */
public record BlogCategoryNode(UUID id, String name, String slug, String description, long publishedPosts,
                               boolean inMenu, List<BlogCategoryNode> subcategories) {
}
