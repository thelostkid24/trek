package com.sahyatri.blog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A blog post, filed under one sub-category. Drafts are admin-only; it's public once {@code publishedAt} is set. */
@Entity
@Table(name = "blog_posts")
public class BlogPost {

    /** What the editor writes; everything but the cover photo, related treks and publishing. */
    public record Content(UUID categoryId, String slug, String title, String excerpt, String body, String coverCaption,
                          LocalDate coverTakenOn, String authorName, String faqsJson, BlogSchemaType schemaType,
                          String researchNotes) {
    }

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID categoryId;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String title;

    /** Up to 160 characters: the line on cards and the search-result description. */
    private String excerpt;

    /** Paragraphs, "## " headings, "- " lists and the post's own photos as ![caption](url); see docs/TRD.md §7.19. */
    @Column(nullable = false)
    private String body;

    private UUID coverPhotoId;

    /** Shown under the hero image with {@link #coverTakenOn}, e.g. "Juda ka Talab, frozen" · 12 Jan 2026. */
    private String coverCaption;

    private LocalDate coverTakenOn;

    @Column(nullable = false, updatable = false)
    private UUID authorId;

    /** The byline when set; otherwise the author's account name. */
    private String authorName;

    /** JSON array of {question, answer}. */
    @Column(columnDefinition = "jsonb", nullable = false)
    @ColumnTransformer(write = "?::jsonb")
    private String faqs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BlogSchemaType schemaType;

    /** Admin only; never public. */
    private String researchNotes;

    private Instant publishedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected BlogPost() {
    }

    public BlogPost(UUID authorId, Content content) {
        this.id = UUID.randomUUID();
        this.authorId = authorId;
        this.createdAt = Instant.now();
        edit(content);
    }

    public void edit(Content c) {
        this.categoryId = c.categoryId();
        this.slug = c.slug();
        this.title = c.title();
        this.excerpt = c.excerpt();
        this.body = c.body();
        this.coverCaption = c.coverCaption();
        this.coverTakenOn = c.coverTakenOn();
        this.authorName = c.authorName();
        this.faqs = c.faqsJson();
        this.schemaType = c.schemaType();
        this.researchNotes = c.researchNotes();
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

    public String getCoverCaption() {
        return coverCaption;
    }

    public LocalDate getCoverTakenOn() {
        return coverTakenOn;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getFaqs() {
        return faqs;
    }

    public BlogSchemaType getSchemaType() {
        return schemaType;
    }

    public String getResearchNotes() {
        return researchNotes;
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
