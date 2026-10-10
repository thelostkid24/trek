package com.sahyatri.blog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A blog category, e.g. "Kedarkantha", or a sub-category under one ({@code parentId} set; one level deep). */
@Entity
@Table(name = "blog_categories")
public class BlogCategory {

    @Id
    private UUID id;

    private UUID parentId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected BlogCategory() {
    }

    public BlogCategory(UUID parentId, String name, String slug) {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        update(parentId, name, slug);
    }

    public void update(UUID parentId, String name, String slug) {
        this.parentId = parentId;
        this.name = name;
        this.slug = slug;
    }

    public UUID getId() {
        return id;
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }
}
