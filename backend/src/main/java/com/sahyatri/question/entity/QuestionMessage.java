package com.sahyatri.question.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One message in a question thread. Never edited. */
@Entity
@Table(name = "question_messages")
public class QuestionMessage {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID threadId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuthorRole authorRole;

    /** Null for a visitor. */
    @Column(updatable = false)
    private UUID authorId;

    @Column(nullable = false, updatable = false)
    private String body;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected QuestionMessage() {
    }

    public QuestionMessage(UUID threadId, AuthorRole authorRole, UUID authorId, String body, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.threadId = threadId;
        this.authorRole = authorRole;
        this.authorId = authorId;
        this.body = body;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getThreadId() {
        return threadId;
    }

    public AuthorRole getAuthorRole() {
        return authorRole;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
