package com.sahyatri.blog.dto;

import java.util.UUID;

/** One of the post's photos as its cover, or null for none. */
public record BlogCoverRequest(UUID photoId) {
}
