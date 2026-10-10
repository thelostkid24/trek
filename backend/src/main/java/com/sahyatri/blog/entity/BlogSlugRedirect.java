package com.sahyatri.blog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A slug a published post gave up; /blog/{oldSlug} redirects to the post's current one. */
@Entity
@Table(name = "blog_slug_redirects")
public class BlogSlugRedirect {

    @Id
    private String oldSlug;

    @Column(nullable = false)
    private UUID postId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected BlogSlugRedirect() {
    }

    public BlogSlugRedirect(String oldSlug, UUID postId) {
        this.oldSlug = oldSlug;
        this.postId = postId;
        this.createdAt = Instant.now();
    }

    public String getOldSlug() {
        return oldSlug;
    }

    public UUID getPostId() {
        return postId;
    }
}
