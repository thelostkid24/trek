package com.sahyatri.blog.dto;

import java.util.List;

/** /blog/{category} or /blog/{category}/{sub-category}: its published posts, newest first. */
public record BlogCategoryPage(BlogCategoryNode category, BlogCategoryRef parent, List<BlogPostSummary> posts,
                               boolean indexable) {
}
