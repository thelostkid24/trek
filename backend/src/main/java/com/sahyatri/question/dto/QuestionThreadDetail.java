package com.sahyatri.question.dto;

import com.sahyatri.question.entity.ThreadStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A whole thread. {@code askerEmail} is only filled for admins; {@code canReply} is false once it's closed. */
public record QuestionThreadDetail(
        UUID id,
        ThreadStatus status,
        UUID guideId,
        String guideName,
        String guideSlug,
        String askerName,
        String askerEmail,
        boolean visitor,
        Instant createdAt,
        Instant firstReplyAt,
        Instant closedAt,
        boolean canReply,
        List<QuestionMessageView> messages) {
}
