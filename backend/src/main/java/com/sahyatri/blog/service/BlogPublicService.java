package com.sahyatri.blog.service;

import com.sahyatri.blog.dto.BlogCategoryNode;
import com.sahyatri.blog.dto.BlogCategoryPage;
import com.sahyatri.blog.dto.BlogCategoryResponse;
import com.sahyatri.blog.dto.BlogPostResponse;
import com.sahyatri.blog.dto.BlogPostSummary;
import com.sahyatri.blog.entity.BlogCategory;
import com.sahyatri.blog.entity.BlogPost;
import com.sahyatri.blog.repository.BlogCategoryRepository;
import com.sahyatri.blog.repository.BlogPostRepository;
import com.sahyatri.blog.repository.BlogSlugRedirectRepository;
import com.sahyatri.common.exception.ApiException;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The public blog (docs/TRD.md §7.19): published posts only, newest first. A category's page exists once it has a
 * published post; it's in the menu and indexed from {@link #MENU_MIN_POSTS}.
 */
@Service
public class BlogPublicService {

    public static final int MENU_MIN_POSTS = 3;
    static final int MAX_LATEST = 100;

    /** A published post, or where a slug it gave up now lives. */
    public record Resolved(BlogPostResponse post, String movedTo) {
    }

    private final BlogCategoryRepository categories;
    private final BlogPostRepository posts;
    private final BlogSlugRedirectRepository redirects;
    private final BlogViews views;

    public BlogPublicService(BlogCategoryRepository categories, BlogPostRepository posts,
                             BlogSlugRedirectRepository redirects, BlogViews views) {
        this.categories = categories;
        this.posts = posts;
        this.redirects = redirects;
        this.views = views;
    }

    /** The ten categories in menu order, each with its sub-categories and published-post counts. */
    @Transactional(readOnly = true)
    public List<BlogCategoryNode> tree() {
        List<BlogCategory> all = categories.findAllByOrderByPositionAscNameAsc();
        Map<UUID, Long> counts = counts();
        return all.stream().filter(BlogCategory::isTopLevel).map(top -> node(top, all, counts)).toList();
    }

    /** The admin's flat list, every category with the posts filed directly under it. */
    @Transactional(readOnly = true)
    public List<BlogCategoryResponse> flat() {
        Map<UUID, Long> counts = counts();
        return categories.findAllByOrderByPositionAscNameAsc().stream()
                .map(c -> new BlogCategoryResponse(c.getId(), c.getParentId(), c.getName(), c.getSlug(),
                        c.getDescription(), c.getPosition(), counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    /** A category's (or sub-category's) page; 404 until it has a published post. */
    @Transactional(readOnly = true)
    public BlogCategoryPage category(String slug) {
        BlogCategory category = categories.findBySlug(slug).orElseThrow(BlogPublicService::categoryNotFound);
        List<BlogCategory> all = categories.findAllByOrderByPositionAscNameAsc();
        BlogCategoryNode node = node(category, all, counts());
        if (node.publishedPosts() == 0) {
            throw categoryNotFound();
        }
        List<UUID> ids = new ArrayList<>(List.of(category.getId()));
        node.subcategories().forEach(s -> ids.add(s.id()));
        Map<UUID, BlogCategory> byId = all.stream().collect(Collectors.toMap(BlogCategory::getId, c -> c));
        return new BlogCategoryPage(node, category.isTopLevel() ? null : views.ref(category.getParentId(), byId),
                views.summaries(posts.findByPublishedAtNotNullAndCategoryIdInOrderByPublishedAtDesc(ids)),
                node.inMenu());
    }

    @Transactional(readOnly = true)
    public List<BlogPostSummary> latest(Integer limit) {
        int n = limit == null ? MAX_LATEST : Math.clamp(limit, 1, MAX_LATEST);
        return views.summaries(posts.findByPublishedAtNotNullOrderByPublishedAtDesc(Limit.of(n)));
    }

    /** A published post by slug, or (for a slug it gave up) where it moved; 404 otherwise. */
    @Transactional(readOnly = true)
    public Resolved post(String slug) {
        var post = posts.findBySlugAndPublishedAtNotNull(slug);
        if (post.isPresent()) {
            return new Resolved(views.full(post.get(), false), null);
        }
        return redirects.findById(slug)
                .flatMap(r -> posts.findById(r.getPostId()))
                .filter(BlogPost::isPublished)
                .map(p -> new Resolved(null, p.getSlug()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "Post not found"));
    }

    /** Paths for the sitemap: every published post and every category in the menu. */
    @Transactional(readOnly = true)
    public List<String> sitemapPaths() {
        List<String> paths = new ArrayList<>();
        for (BlogCategoryNode top : tree()) {
            if (top.inMenu()) {
                paths.add("/blog/" + top.slug());
            }
            top.subcategories().stream().filter(BlogCategoryNode::inMenu)
                    .forEach(s -> paths.add("/blog/" + top.slug() + "/" + s.slug()));
        }
        posts.findByPublishedAtNotNullOrderByPublishedAtDesc(Limit.unlimited())
                .forEach(p -> paths.add("/blog/" + p.getSlug()));
        return paths;
    }

    private Map<UUID, Long> counts() {
        return posts.countPublishedByCategory().stream().collect(Collectors.toMap(
                BlogPostRepository.CategoryCount::getCategoryId, BlogPostRepository.CategoryCount::getPosts));
    }

    private static BlogCategoryNode node(BlogCategory c, List<BlogCategory> all, Map<UUID, Long> counts) {
        List<BlogCategoryNode> subs = all.stream()
                .filter(s -> c.getId().equals(s.getParentId()))
                .map(s -> node(s, all, counts))
                .toList();
        long total = Stream.concat(Stream.of(counts.getOrDefault(c.getId(), 0L)), subs.stream().map(BlogCategoryNode::publishedPosts))
                .mapToLong(Long::longValue).sum();
        return new BlogCategoryNode(c.getId(), c.getName(), c.getSlug(), c.getDescription(), total,
                total >= MENU_MIN_POSTS, subs);
    }

    private static ApiException categoryNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Category not found");
    }
}
