package com.sahyatri.question.dto;

import com.sahyatri.question.entity.AuthorRole;
import com.sahyatri.question.entity.ThreadStatus;

import java.time.Instant;
import java.util.UUID;

/** A row in an inbox. {@code askerEmail} is only filled for admins. */
public record QuestionThreadSummary(
        UUID id,
        ThreadStatus status,
        UUID guideId,
        String guideName,
        String guideSlug,
        String askerName,
        String askerEmail,
        boolean visitor,
        Instant createdAt,
        Instant lastMessageAt,
        AuthorRole lastAuthorRole,
        String preview) {
}
