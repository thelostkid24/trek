package com.sahyatri.question.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A question to a guide and the replies under it (§7.21.6). Asked by an account or by a visitor's proven email. */
@Entity
@Table(name = "question_threads")
public class QuestionThread {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID guideId;

    @Column(updatable = false)
    private UUID askerId;

    @Column(updatable = false)
    private String visitorEmail;

    @Column(nullable = false)
    private String askerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ThreadStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastMessageAt;

    private Instant firstReplyAt;

    private Instant closedAt;

    private UUID closedBy;

    protected QuestionThread() {
    }

    private QuestionThread(UUID guideId, UUID askerId, String visitorEmail, String askerName) {
        this.id = UUID.randomUUID();
        this.guideId = guideId;
        this.askerId = askerId;
        this.visitorEmail = visitorEmail;
        this.askerName = askerName;
        this.status = ThreadStatus.OPEN;
        this.createdAt = Instant.now();
        this.lastMessageAt = createdAt;
    }

    public static QuestionThread byAccount(UUID guideId, UUID askerId, String askerName) {
        return new QuestionThread(guideId, askerId, null, askerName);
    }

    public static QuestionThread byVisitor(UUID guideId, String email, String askerName) {
        return new QuestionThread(guideId, null, email, askerName);
    }

    /** The asker wrote: it waits on the guide again. */
    public void askerWrote(Instant at) {
        status = ThreadStatus.OPEN;
        lastMessageAt = at;
    }

    public void guideWrote(Instant at) {
        status = ThreadStatus.ANSWERED;
        lastMessageAt = at;
        if (firstReplyAt == null) {
            firstReplyAt = at;
        }
    }

    public void close(UUID adminId, Instant at) {
        status = ThreadStatus.CLOSED;
        closedAt = at;
        closedBy = adminId;
        lastMessageAt = at;
    }

    public boolean isClosed() {
        return status == ThreadStatus.CLOSED;
    }

    public boolean isVisitor() {
        return askerId == null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGuideId() {
        return guideId;
    }

    public UUID getAskerId() {
        return askerId;
    }

    public String getVisitorEmail() {
        return visitorEmail;
    }

    public String getAskerName() {
        return askerName;
    }

    public ThreadStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    public Instant getFirstReplyAt() {
        return firstReplyAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
