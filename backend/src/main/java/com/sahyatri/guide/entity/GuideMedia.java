package com.sahyatri.guide.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A photo (file at guide-media/<id>.jpg) or a YouTube video (its id) on the guide's profile, in the guide's order. */
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

    /** The 11-character YouTube video id; null for photos. */
    private String youtubeId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected GuideMedia() {
    }

    private GuideMedia(UUID guideId, GuideMediaKind kind, String caption, int position, String youtubeId) {
        this.id = UUID.randomUUID();
        this.guideId = guideId;
        this.kind = kind;
        this.caption = caption;
        this.position = position;
        this.youtubeId = youtubeId;
        this.createdAt = Instant.now();
    }

    public static GuideMedia photo(UUID guideId, String caption, int position) {
        return new GuideMedia(guideId, GuideMediaKind.PHOTO, caption, position, null);
    }

    public static GuideMedia youtube(UUID guideId, String caption, int position, String youtubeId) {
        return new GuideMedia(guideId, GuideMediaKind.VIDEO, caption, position, youtubeId);
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public void setPosition(int position) {
        this.position = position;
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

    public String getYoutubeId() {
        return youtubeId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
