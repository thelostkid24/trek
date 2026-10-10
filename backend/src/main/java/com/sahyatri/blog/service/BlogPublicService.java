package com.sahyatri.blog.service;

import com.sahyatri.blog.dto.BlogCategoryResponse;
import com.sahyatri.blog.dto.BlogPostResponse;
import com.sahyatri.blog.dto.BlogPostSummary;
import com.sahyatri.blog.entity.BlogCategory;
import com.sahyatri.blog.entity.BlogPost;
import com.sahyatri.blog.repository.BlogCategoryRepository;
import com.sahyatri.blog.repository.BlogPostRepository;
import com.sahyatri.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** The public blog (docs/TRD.md §7.19): published posts only, newest first. */
@Service
public class BlogPublicService {

    private final BlogCategoryRepository categories;
    private final BlogPostRepository posts;
    private final BlogViews views;

    public BlogPublicService(BlogCategoryRepository categories, BlogPostRepository posts, BlogViews views) {
        this.categories = categories;
        this.posts = posts;
        this.views = views;
    }

    /** Every category with its published-post count; the page hides the empty ones. */
    @Transactional(readOnly = true)
    public List<BlogCategoryResponse> categories() {
        Map<UUID, Long> counts = posts.countPublishedByCategory().stream()
                .collect(Collectors.toMap(BlogPostRepository.CategoryCount::getCategoryId,
                        BlogPostRepository.CategoryCount::getPosts));
        return categories.findAllByOrderByNameAsc().stream()
                .map(c -> new BlogCategoryResponse(c.getId(), c.getParentId(), c.getName(), c.getSlug(),
                        counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    /** Published posts; a category's own and its sub-categories' when {@code categorySlug} is given. */
    @Transactional(readOnly = true)
    public List<BlogPostSummary> posts(String categorySlug) {
        if (categorySlug == null || categorySlug.isBlank()) {
            return views.summaries(posts.findByPublishedAtNotNullOrderByPublishedAtDesc());
        }
        BlogCategory category = categories.findBySlug(categorySlug)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Category not found"));
        List<UUID> ids = new ArrayList<>(List.of(category.getId()));
        categories.findByParentId(category.getId()).forEach(c -> ids.add(c.getId()));
        return views.summaries(posts.findByPublishedAtNotNullAndCategoryIdInOrderByPublishedAtDesc(ids));
    }

    @Transactional(readOnly = true)
    public BlogPostResponse post(String slug) {
        BlogPost post = posts.findBySlugAndPublishedAtNotNull(slug)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "Post not found"));
        return views.full(post);
    }

    /** Slugs of every published post, for the sitemap. */
    @Transactional(readOnly = true)
    public List<String> publishedSlugs() {
        return posts.findByPublishedAtNotNullOrderByPublishedAtDesc().stream().map(BlogPost::getSlug).toList();
    }
}
