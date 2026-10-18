package com.sahyatri.blog.dto;

import java.util.List;

/** Slugs added as drafts, and slugs left alone because a post already has them. */
public record BlogImportResponse(List<String> created, List<String> skipped) {
}
