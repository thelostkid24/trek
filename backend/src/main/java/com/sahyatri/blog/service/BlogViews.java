package com.sahyatri.blog.service;

import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.blog.dto.BlogCategoryRef;
import com.sahyatri.blog.dto.BlogPhotoResponse;
import com.sahyatri.blog.dto.BlogPostResponse;
import com.sahyatri.blog.dto.BlogPostSummary;
import com.sahyatri.blog.entity.BlogCategory;
import com.sahyatri.blog.entity.BlogPhoto;
import com.sahyatri.blog.entity.BlogPost;
import com.sahyatri.blog.repository.BlogCategoryRepository;
import com.sahyatri.blog.repository.BlogPhotoRepository;
import com.sahyatri.common.storage.BlogPhotoFiles;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Turns posts into what the admin and public endpoints return. Callers hold the transaction. */
@Component
class BlogViews {

    private final BlogCategoryRepository categories;
    private final BlogPhotoRepository photos;
    private final UserRepository users;
    private final BlogPhotoFiles files;

    BlogViews(BlogCategoryRepository categories, BlogPhotoRepository photos, UserRepository users, BlogPhotoFiles files) {
        this.categories = categories;
        this.photos = photos;
        this.users = users;
        this.files = files;
    }

    /** Every category by id; there are few, so lists resolve theirs from one read. */
    Map<UUID, BlogCategory> categoriesById() {
        return categories.findAll().stream().collect(Collectors.toMap(BlogCategory::getId, Function.identity()));
    }

    BlogCategoryRef ref(UUID categoryId, Map<UUID, BlogCategory> all) {
        BlogCategory c = all.get(categoryId);
        if (c == null) {
            return null;
        }
        return new BlogCategoryRef(c.getId(), c.getName(), c.getSlug(),
                c.getParentId() == null ? null : ref(c.getParentId(), all));
    }

    List<BlogPostSummary> summaries(List<BlogPost> posts) {
        Map<UUID, BlogCategory> all = categoriesById();
        return posts.stream().map(p -> new BlogPostSummary(p.getId(), p.getSlug(), p.getTitle(), p.getExcerpt(),
                ref(p.getCategoryId(), all), coverUrl(p), p.getPublishedAt(), p.getUpdatedAt())).toList();
    }

    BlogPostResponse full(BlogPost p) {
        List<BlogPhotoResponse> photoList = photos.findByPostIdOrderByCreatedAtAscIdAsc(p.getId()).stream()
                .map(this::photo)
                .toList();
        String author = users.findById(p.getAuthorId()).map(User::getFullName).orElse(null);
        return new BlogPostResponse(p.getId(), p.getSlug(), p.getTitle(), p.getExcerpt(), p.getBody(),
                ref(p.getCategoryId(), categoriesById()), p.getCoverPhotoId(), coverUrl(p), photoList, author,
                p.getPublishedAt(), p.getUpdatedAt());
    }

    BlogPhotoResponse photo(BlogPhoto photo) {
        return new BlogPhotoResponse(photo.getId(), files.url(photo.getId()), photo.getCaption());
    }

    private String coverUrl(BlogPost p) {
        return p.getCoverPhotoId() == null ? null : files.url(p.getCoverPhotoId());
    }
}
