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
import com.sahyatri.blog.repository.BlogCategoryRepository;
import com.sahyatri.blog.repository.BlogPhotoRepository;
import com.sahyatri.blog.repository.BlogPostRepository;
import com.sahyatri.catalog.service.TrackPhotoService;
import com.sahyatri.common.audit.AuditLog;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.common.storage.BlogPhotoFiles;
import com.sahyatri.common.storage.FileStorage;
import com.sahyatri.common.storage.Images;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Writing the blog (docs/TRD.md §7.19): categories one level deep, posts as drafts until published, and each post's
 * photos. Photos are re-encoded like trek photos; the file is written before its row and removed after it, so a
 * public URL never points at a missing file.
 */
@Service
public class BlogAdminService {

    static final int MAX_PHOTOS = 30;

    private final BlogCategoryRepository categories;
    private final BlogPostRepository posts;
    private final BlogPhotoRepository photos;
    private final BlogViews views;
    private final FileStorage storage;
    private final AuditLog audit;

    public BlogAdminService(BlogCategoryRepository categories, BlogPostRepository posts, BlogPhotoRepository photos,
                            BlogViews views, FileStorage storage, AuditLog audit) {
        this.categories = categories;
        this.posts = posts;
        this.photos = photos;
        this.views = views;
        this.storage = storage;
        this.audit = audit;
    }

    // --- Categories (listed by BlogPublicService#categories, the same for both)

    @Transactional
    public BlogCategoryResponse createCategory(BlogCategoryRequest req) {
        BlogCategory category = new BlogCategory(null, req.name().trim(), req.slug());
        return saveCategory(category, req);
    }

    @Transactional
    public BlogCategoryResponse updateCategory(UUID id, BlogCategoryRequest req) {
        return saveCategory(requireCategory(id), req);
    }

    /** Only an empty category goes: no sub-categories and no posts, published or not. */
    @Transactional
    public void deleteCategory(UUID id) {
        BlogCategory category = requireCategory(id);
        if (categories.existsByParentId(id)) {
            throw ApiException.conflict("CATEGORY_IN_USE", "Delete or move its sub-categories first");
        }
        if (posts.existsByCategoryId(id)) {
            throw ApiException.conflict("CATEGORY_IN_USE", "Move or delete its posts first");
        }
        categories.delete(category);
    }

    private BlogCategoryResponse saveCategory(BlogCategory category, BlogCategoryRequest req) {
        UUID parentId = req.parentId();
        if (parentId != null) {
            BlogCategory parent = categories.findById(parentId)
                    .orElseThrow(() -> ApiException.validation("parent_id", "no such category"));
            if (parent.getId().equals(category.getId()) || parent.getParentId() != null) {
                throw ApiException.validation("parent_id", "must be a top-level category");
            }
            if (categories.existsByParentId(category.getId())) {
                throw ApiException.validation("parent_id", "has sub-categories, so it must stay top-level");
            }
        }
        if (categories.existsBySlugAndIdNot(req.slug(), category.getId())) {
            throw ApiException.conflict("SLUG_TAKEN", "Another category already uses this slug");
        }
        category.update(parentId, req.name().trim(), req.slug());
        return toResponse(categories.saveAndFlush(category), posts.countByCategoryIdAndPublishedAtNotNull(category.getId()));
    }

    private BlogCategory requireCategory(UUID id) {
        return categories.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Category not found"));
    }

    private static BlogCategoryResponse toResponse(BlogCategory c, long published) {
        return new BlogCategoryResponse(c.getId(), c.getParentId(), c.getName(), c.getSlug(), published);
    }

    // --- Posts

    /** Drafts and published posts, last edited first. */
    @Transactional(readOnly = true)
    public List<BlogPostSummary> posts() {
        return views.summaries(posts.findAllByOrderByUpdatedAtDesc());
    }

    @Transactional(readOnly = true)
    public BlogPostResponse post(UUID id) {
        return views.full(requirePost(id));
    }

    /** A new post starts as a draft. */
    @Transactional
    public BlogPostResponse createPost(UUID adminId, BlogPostRequest req) {
        checkPost(null, req);
        BlogPost post = new BlogPost(adminId, req.categoryId(), req.slug(), req.title().trim(), blankToNull(req.excerpt()),
                req.body().strip());
        return views.full(posts.saveAndFlush(post));
    }

    @Transactional
    public BlogPostResponse updatePost(UUID id, BlogPostRequest req) {
        BlogPost post = requirePost(id);
        checkPost(id, req);
        post.edit(req.categoryId(), req.slug(), req.title().trim(), blankToNull(req.excerpt()), req.body().strip());
        return views.full(posts.saveAndFlush(post));
    }

    /** Publishing shows the post on /blog dated now; unpublishing takes it back to a draft. */
    @Transactional
    public BlogPostResponse setPublished(UUID adminId, UUID id, boolean published) {
        BlogPost post = requirePost(id);
        if (post.isPublished() != published) {
            if (published) {
                post.publish(Instant.now());
            } else {
                post.unpublish();
            }
            audit.record(adminId, published ? "BLOG_POST_PUBLISHED" : "BLOG_POST_UNPUBLISHED", "BLOG_POST", id,
                    Map.of("slug", post.getSlug()));
        }
        return views.full(posts.saveAndFlush(post));
    }

    @Transactional
    public BlogPostResponse setCover(UUID id, UUID photoId) {
        BlogPost post = requirePost(id);
        if (photoId != null && photos.findByIdAndPostId(photoId, id).isEmpty()) {
            throw ApiException.validation("photo_id", "must be one of this post's photos");
        }
        post.setCoverPhotoId(photoId);
        return views.full(posts.saveAndFlush(post));
    }

    /** The post, its photos and their files. */
    public void deletePost(UUID adminId, UUID id) {
        BlogPost post = requirePost(id);
        List<UUID> photoIds = photos.findByPostIdOrderByCreatedAtAscIdAsc(id).stream().map(BlogPhoto::getId).toList();
        posts.delete(post);
        audit.record(adminId, "BLOG_POST_DELETED", "BLOG_POST", id, Map.of("slug", post.getSlug()));
        photoIds.forEach(this::deleteQuietly);
    }

    private void checkPost(UUID id, BlogPostRequest req) {
        if (!categories.existsById(req.categoryId())) {
            throw ApiException.validation("category_id", "no such category");
        }
        if (posts.existsBySlugAndIdNot(req.slug(), id == null ? new UUID(0, 0) : id)) {
            throw ApiException.conflict("SLUG_TAKEN", "Another post already uses this slug");
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

    /** Also clears it as the cover. The body may still mention it; the page skips photos that aren't the post's. */
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
