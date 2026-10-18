package com.sahyatri.blog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** A batch of posts to add as drafts, e.g. an article pack (docs/TRD.md §7.19). */
public record BlogImportRequest(@NotEmpty @Size(max = 100) List<@Valid BlogImportPost> posts) {
}
