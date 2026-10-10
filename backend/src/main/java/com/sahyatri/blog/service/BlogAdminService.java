package com.sahyatri.blog.service;

import com.sahyatri.blog.dto.BlogCategoryRequest;
import com.sahyatri.blog.dto.BlogCategoryResponse;
import com.sahyatri.blog.dto.BlogPhotoResponse;
import com.sahyatri.blog.dto.BlogPostRequest;
import com.sahyatri.blog.dto.BlogPostResponse;
import com.sahyatri.blog.dto.BlogPostSummary;
import com.sahyatri.blog.entity.BlogCategory;
import com.sahyatri.blog.entity.BlogPhoto;
import com.sahyatri.blog.entity.BlogPost;
import com.sahyatri.blog.entity.BlogSchemaType;
import com.sahyatri.blog.entity.BlogSlugRedirect;
import com.sahyatri.blog.repository.BlogCategoryRepository;
import com.sahyatri.blog.repository.BlogPhotoRepository;
import com.sahyatri.blog.repository.BlogPostRepository;
import com.sahyatri.blog.repository.BlogSlugRedirectRepository;
import com.sahyatri.catalog.service.TrackPhotoService;
import com.sahyatri.common.audit.AuditLog;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.common.storage.BlogPhotoFiles;
import com.sahyatri.common.storage.FileStorage;
import com.sahyatri.common.storage.Images;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Writing the blog (docs/TRD.md §7.19). The ten categories are seeded; admins add sub-categories under them and file
 * each post under exactly one sub-category. Posts are drafts until published, and publishing needs the hero caption
 * and date, an excerpt and the editor's confirmation that no money figures are published (the money rule). A
 * published post that changes slug keeps its old URL as a redirect. Photos are re-encoded like trek photos; the file
 * is written before its row and removed after it, so a public URL never points at a missing file.
 */
@Service
public class BlogAdminService {

    static final int MAX_PHOTOS = 30;

    private final BlogCategoryRepository categories;
    private final BlogPostRepository posts;
    private final BlogPhotoRepository photos;
    private final BlogSlugRedirectRepository redirects;
    private final BlogViews views;
    private final FileStorage storage;
    private final JdbcTemplate jdbc;
    private final AuditLog audit;

    public BlogAdminService(BlogCategoryRepository categories, BlogPostRepository posts, BlogPhotoRepository photos,
                            BlogSlugRedirectRepository redirects, BlogViews views, FileStorage storage,
                            JdbcTemplate jdbc, AuditLog audit) {
        this.categories = categories;
        this.posts = posts;
        this.photos = photos;
        this.redirects = redirects;
        this.views = views;
        this.storage = storage;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    // --- Categories (listed by BlogPublicService#flat)

    /** A new sub-category under one of the ten. */
    @Transactional
    public BlogCategoryResponse createCategory(BlogCategoryRequest req) {
        if (req.parentId() == null) {
            throw ApiException.validation("parent_id", "pick one of the ten categories");
        }
        BlogCategory parent = categories.findById(req.parentId())
                .filter(BlogCategory::isTopLevel)
                .orElseThrow(() -> ApiException.validation("parent_id", "must be one of the ten categories"));
        checkCategorySlug(req.slug(), null);
        int position = (int) categories.findAll().stream().filter(c -> parent.getId().equals(c.getParentId())).count() + 1;
        BlogCategory category = new BlogCategory(parent.getId(), req.name().trim(), req.slug(),
                blankToNull(req.description()), position);
        return toResponse(categories.saveAndFlush(category));
    }

    /** Name, slug and description; where it sits never changes. */
    @Transactional
    public BlogCategoryResponse updateCategory(UUID id, BlogCategoryRequest req) {
        BlogCategory category = requireCategory(id);
        checkCategorySlug(req.slug(), id);
        category.update(req.name().trim(), req.slug(), blankToNull(req.description()));
        return toResponse(categories.saveAndFlush(category));
    }

    /** Only an empty sub-category goes; the ten stay. */
    @Transactional
    public void deleteCategory(UUID id) {
        BlogCategory category = requireCategory(id);
        if (category.isTopLevel()) {
            throw ApiException.conflict("CATEGORY_IN_USE", "The ten categories can't be deleted");
        }
        if (posts.existsByCategoryId(id)) {
            throw ApiException.conflict("CATEGORY_IN_USE", "Move or delete its posts first");
        }
        categories.delete(category);
    }

    /** Category and post slugs share /blog/<slug>, so a category can't take a post's (or a post's old) slug. */
    private void checkCategorySlug(String slug, UUID id) {
        boolean taken = id == null ? categories.existsBySlug(slug) : categories.existsBySlugAndIdNot(slug, id);
        if (taken || posts.existsBySlug(slug) || redirects.existsById(slug)) {
            throw ApiException.conflict("SLUG_TAKEN", "A category or post already uses this slug");
        }
    }

    private BlogCategory requireCategory(UUID id) {
        return categories.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Category not found"));
    }

    private BlogCategoryResponse toResponse(BlogCategory c) {
        return new BlogCategoryResponse(c.getId(), c.getParentId(), c.getName(), c.getSlug(), c.getDescription(),
                c.getPosition(), 0);
    }

    // --- Posts

    /** Drafts and published posts, last edited first. */
    @Transactional(readOnly = true)
    public List<BlogPostSummary> posts() {
        return views.summaries(posts.findAllByOrderByUpdatedAtDesc());
    }

    @Transactional(readOnly = true)
    public BlogPostResponse post(UUID id) {
        return views.full(requirePost(id), true);
    }

    /** A new post starts as a draft. */
    @Transactional
    public BlogPostResponse createPost(UUID adminId, BlogPostRequest req) {
        checkPost(null, req);
        BlogPost post = posts.saveAndFlush(new BlogPost(adminId, content(req)));
        linkTreks(post.getId(), req.trackIds());
        return views.full(post, true);
    }

    @Transactional
    public BlogPostResponse updatePost(UUID id, BlogPostRequest req) {
        BlogPost post = requirePost(id);
        checkPost(id, req);
        String oldSlug = post.getSlug();
        if (!oldSlug.equals(req.slug())) {
            redirects.deleteById(req.slug()); // its own old slug, taken back
            if (post.isPublished()) {
                redirects.save(new BlogSlugRedirect(oldSlug, id));
            }
        }
        post.edit(content(req));
        posts.saveAndFlush(post);
        linkTreks(id, req.trackIds());
        return views.full(post, true);
    }

    /**
     * Publishing shows the post on /blog dated now; unpublishing takes it back to a draft. Publishing needs the hero
     * photo with its caption and date, an excerpt, and {@code moneyRuleConfirmed}.
     */
    @Transactional
    public BlogPostResponse setPublished(UUID adminId, UUID id, boolean published, boolean moneyRuleConfirmed) {
        BlogPost post = requirePost(id);
        if (published && !post.isPublished()) {
            Map<String, String> missing = new LinkedHashMap<>();
            if (post.getCoverPhotoId() == null) {
                missing.put("cover_photo_id", "pick a hero image");
            }
            if (post.getCoverCaption() == null) {
                missing.put("cover_caption", "give the hero image a caption");
            }
            if (post.getCoverTakenOn() == null) {
                missing.put("cover_taken_on", "say when the hero image was taken");
            }
            if (post.getExcerpt() == null) {
                missing.put("excerpt", "write the excerpt; it's the Google description");
            }
            if (!moneyRuleConfirmed) {
                missing.put("money_rule_confirmed", "confirm the post publishes none of our money");
            }
            if (!missing.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Not ready to publish",
                        Map.of("fields", missing));
            }
        }
        if (post.isPublished() != published) {
            if (published) {
                post.publish(Instant.now());
            } else {
                post.unpublish();
            }
            audit.record(adminId, published ? "BLOG_POST_PUBLISHED" : "BLOG_POST_UNPUBLISHED", "BLOG_POST", id,
                    Map.of("slug", post.getSlug()));
        }
        return views.full(posts.saveAndFlush(post), true);
    }

    @Transactional
    public BlogPostResponse setCover(UUID id, UUID photoId) {
        BlogPost post = requirePost(id);
        if (photoId != null && photos.findByIdAndPostId(photoId, id).isEmpty()) {
            throw ApiException.validation("photo_id", "must be one of this post's photos");
        }
        post.setCoverPhotoId(photoId);
        return views.full(posts.saveAndFlush(post), true);
    }

    /** The post, its photos and their files, and any old slugs. */
    public void deletePost(UUID adminId, UUID id) {
        BlogPost post = requirePost(id);
        List<UUID> photoIds = photos.findByPostIdOrderByCreatedAtAscIdAsc(id).stream().map(BlogPhoto::getId).toList();
        posts.delete(post);
        audit.record(adminId, "BLOG_POST_DELETED", "BLOG_POST", id, Map.of("slug", post.getSlug()));
        photoIds.forEach(this::deleteQuietly);
    }

    private void checkPost(UUID id, BlogPostRequest req) {
        BlogCategory category = categories.findById(req.categoryId())
                .orElseThrow(() -> ApiException.validation("category_id", "no such category"));
        if (category.isTopLevel()) {
            throw ApiException.validation("category_id", "pick a sub-category, not a whole category");
        }
        boolean postTaken = id == null ? posts.existsBySlug(req.slug()) : posts.existsBySlugAndIdNot(req.slug(), id);
        boolean redirectTaken = redirects.findById(req.slug()).filter(r -> !r.getPostId().equals(id)).isPresent();
        if (postTaken || redirectTaken || categories.existsBySlug(req.slug())) {
            throw ApiException.conflict("SLUG_TAKEN", "A post or category already uses this slug");
        }
        if (req.trackIds() != null && !req.trackIds().isEmpty()) {
            Set<UUID> wanted = new LinkedHashSet<>(req.trackIds());
            Integer found = jdbc.queryForObject("SELECT count(*) FROM tracks WHERE id = ANY (?)", Integer.class,
                    (Object) wanted.toArray(UUID[]::new));
            if (found == null || found != wanted.size()) {
                throw ApiException.validation("track_ids", "no such trek");
            }
        }
    }

    private BlogPost.Content content(BlogPostRequest req) {
        return new BlogPost.Content(req.categoryId(), req.slug(), req.title().trim(), blankToNull(req.excerpt()),
                req.body().strip(), blankToNull(req.coverCaption()), req.coverTakenOn(), blankToNull(req.authorName()),
                views.faqsJson(req.faqs()), req.schemaType() == null ? BlogSchemaType.ARTICLE : req.schemaType(),
                blankToNull(req.researchNotes()));
    }

    private void linkTreks(UUID postId, List<UUID> trackIds) {
        jdbc.update("DELETE FROM blog_post_treks WHERE post_id = ?", postId);
        if (trackIds != null) {
            new LinkedHashSet<>(trackIds).forEach(t ->
                    jdbc.update("INSERT INTO blog_post_treks (post_id, track_id) VALUES (?, ?)", postId, t));
        }
    }

    private BlogPost requirePost(UUID id) {
        return posts.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "Post not found"));
    }

    // --- Photos

    public BlogPhotoResponse uploadPhoto(UUID postId, MultipartFile file, String caption) {
        requirePost(postId);
        String trimmed = blankToNull(caption);
        if (trimmed != null && trimmed.length() > 200) {
            throw ApiException.validation("caption", "must be at most 200 characters");
        }
        if (photos.countByPostId(postId) >= MAX_PHOTOS) {
            throw ApiException.conflict("TOO_MANY_PHOTOS", "A post can have at most " + MAX_PHOTOS + " photos");
        }
        byte[] jpeg = TrackPhotoService.toPhotoJpeg(Images.read(file));
        BlogPhoto photo = new BlogPhoto(postId, trimmed);
        storage.put(BlogPhotoFiles.storageKey(photo.getId()), jpeg);
        try {
            photos.saveAndFlush(photo);
        } catch (RuntimeException e) {
            deleteQuietly(photo.getId());
            throw e;
        }
        return views.photo(photo);
    }

    @Transactional
    public BlogPhotoResponse describePhoto(UUID postId, UUID photoId, String caption) {
        BlogPhoto photo = requirePhoto(postId, photoId);
        photo.setCaption(blankToNull(caption));
        return views.photo(photos.saveAndFlush(photo));
    }

    /** Also clears it as the hero image. The body may still mention it; the page skips photos that aren't the post's. */
    public void deletePhoto(UUID postId, UUID photoId) {
        BlogPhoto photo = requirePhoto(postId, photoId);
        photos.delete(photo);
        deleteQuietly(photoId);
    }

    private BlogPhoto requirePhoto(UUID postId, UUID photoId) {
        return photos.findByIdAndPostId(photoId, postId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PHOTO_NOT_FOUND", "Photo not found"));
    }

    private void deleteQuietly(UUID photoId) {
        try {
            storage.delete(BlogPhotoFiles.storageKey(photoId));
        } catch (RuntimeException ignored) {
            // An orphaned file is harmless; nothing points at it.
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
