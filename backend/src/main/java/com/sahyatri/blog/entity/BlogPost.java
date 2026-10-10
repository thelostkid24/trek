package com.sahyatri.blog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A blog post. Drafts are admin-only; it's public once {@code publishedAt} is set. */
@Entity
@Table(name = "blog_posts")
public class BlogPost {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID categoryId;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String title;

    /** The line under the title on cards and in search results. */
    private String excerpt;

    /** Paragraphs, "## " headings, "- " lists and the post's own photos as ![caption](url); see docs/TRD.md §7.19. */
    @Column(nullable = false)
    private String body;

    private UUID coverPhotoId;

    @Column(nullable = false, updatable = false)
    private UUID authorId;

    private Instant publishedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected BlogPost() {
    }

    public BlogPost(UUID authorId, UUID categoryId, String slug, String title, String excerpt, String body) {
        this.id = UUID.randomUUID();
        this.authorId = authorId;
        this.createdAt = Instant.now();
        edit(categoryId, slug, title, excerpt, body);
    }

    public void edit(UUID categoryId, String slug, String title, String excerpt, String body) {
        this.categoryId = categoryId;
        this.slug = slug;
        this.title = title;
        this.excerpt = excerpt;
        this.body = body;
        this.updatedAt = Instant.now();
    }

    public void publish(Instant now) {
        this.publishedAt = now;
        this.updatedAt = now;
    }

    public void unpublish() {
        this.publishedAt = null;
        this.updatedAt = Instant.now();
    }

    public void setCoverPhotoId(UUID coverPhotoId) {
        this.coverPhotoId = coverPhotoId;
        this.updatedAt = Instant.now();
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public String getBody() {
        return body;
    }

    public UUID getCoverPhotoId() {
        return coverPhotoId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
