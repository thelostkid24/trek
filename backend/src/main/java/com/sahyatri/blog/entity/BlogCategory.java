package com.sahyatri.blog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One of the ten blog categories (seeded by V20), or a sub-category under one ({@code parentId} set; one level deep).
 * Posts are filed under sub-categories only.
 */
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

    /** A line under the name on its page. */
    private String description;

    /** Order in menus, lowest first. */
    @Column(nullable = false)
    private int position;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected BlogCategory() {
    }

    public BlogCategory(UUID parentId, String name, String slug, String description, int position) {
        this.id = UUID.randomUUID();
        this.parentId = parentId;
        this.position = position;
        this.createdAt = Instant.now();
        update(name, slug, description);
    }

    public void update(String name, String slug, String description) {
        this.name = name;
        this.slug = slug;
        this.description = description;
    }

    public boolean isTopLevel() {
        return parentId == null;
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

    public String getDescription() {
        return description;
    }

    public int getPosition() {
        return position;
    }
}
