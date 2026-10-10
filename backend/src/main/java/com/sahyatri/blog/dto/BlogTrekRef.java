package com.sahyatri.blog.dto;

import java.util.UUID;

/** A trek a post is about. */
public record BlogTrekRef(UUID id, String slug, String name) {
}
