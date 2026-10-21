package com.sahyatri.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Guide profile videos (`app.guide-media`, §7.21.4). Defaults apply when unset.
 *
 * @param uploadTtl  how long a presigned video upload URL works
 * @param playTtl    how long a presigned playback URL works (a page left open longer re-fetches it)
 * @param pendingTtl a video not confirmed within this is deleted by the cleanup job
 */
@ConfigurationProperties("app.guide-media")
public record GuideMediaProperties(Duration uploadTtl, Duration playTtl, Duration pendingTtl) {

    public GuideMediaProperties {
        uploadTtl = uploadTtl == null ? Duration.ofMinutes(30) : uploadTtl;
        playTtl = playTtl == null ? Duration.ofHours(1) : playTtl;
        pendingTtl = pendingTtl == null ? Duration.ofDays(1) : pendingTtl;
    }
}
