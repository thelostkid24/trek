package com.sahyatri.question.dto;

import com.sahyatri.question.entity.AuthorRole;

import java.time.Instant;
import java.util.UUID;

/** {@code authorName}: the asker's name, the guide's name, or "The Empty Valley" for an admin. */
public record QuestionMessageView(UUID id, AuthorRole authorRole, String authorName, String body, Instant createdAt) {
}
