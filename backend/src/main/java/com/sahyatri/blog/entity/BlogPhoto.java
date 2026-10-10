package com.sahyatri.blog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A photo uploaded to a post, for its cover or its body. The id is also its storage key. */
@Entity
@Table(name = "blog_photos")
public class BlogPhoto {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID postId;

    /** Also the image's alt text. */
    private String caption;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected BlogPhoto() {
    }

    public BlogPhoto(UUID postId, String caption) {
        this.id = UUID.randomUUID();
        this.postId = postId;
        this.caption = caption;
        this.createdAt = Instant.now();
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPostId() {
        return postId;
    }

    public String getCaption() {
        return caption;
    }
}
