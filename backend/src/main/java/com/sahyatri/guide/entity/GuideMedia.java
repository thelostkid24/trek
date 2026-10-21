package com.sahyatri.guide.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A photo or short video on the guide's profile (§7.21.4). Files: {@code GuideMediaFiles}. */
@Entity
@Table(name = "guide_media")
public class GuideMedia {

    @Id
    private UUID id;

    private UUID guideId;

    @Enumerated(EnumType.STRING)
    private GuideMediaKind kind;

    private String caption;

    private int position;

    @Column(name = "duration_s")
    private Integer durationS;

    private long sizeBytes;

    private UUID posterKey;

    @Enumerated(EnumType.STRING)
    private GuideMediaStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected GuideMedia() {
    }

    private GuideMedia(UUID guideId, GuideMediaKind kind, String caption, int position, Integer durationS,
                       long sizeBytes, GuideMediaStatus status) {
        this.id = UUID.randomUUID();
        this.guideId = guideId;
        this.kind = kind;
        this.caption = caption;
        this.position = position;
        this.durationS = durationS;
        this.sizeBytes = sizeBytes;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public static GuideMedia photo(UUID guideId, String caption, int position, long sizeBytes) {
        return new GuideMedia(guideId, GuideMediaKind.PHOTO, caption, position, null, sizeBytes,
                GuideMediaStatus.READY);
    }

    public static GuideMedia pendingVideo(UUID guideId, String caption, int position, int durationS, long sizeBytes) {
        return new GuideMedia(guideId, GuideMediaKind.VIDEO, caption, position, durationS, sizeBytes,
                GuideMediaStatus.PENDING);
    }

    public void ready() {
        this.status = GuideMediaStatus.READY;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public void setPosterKey(UUID posterKey) {
        this.posterKey = posterKey;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGuideId() {
        return guideId;
    }

    public GuideMediaKind getKind() {
        return kind;
    }

    public String getCaption() {
        return caption;
    }

    public int getPosition() {
        return position;
    }

    public Integer getDurationS() {
        return durationS;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public UUID getPosterKey() {
        return posterKey;
    }

    public GuideMediaStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
