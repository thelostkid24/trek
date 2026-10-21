package com.sahyatri.common.storage;

import com.sahyatri.common.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Where guide profile media live in {@link FileStorage} (§7.21.4). Photos and video posters are re-encoded JPEGs
 * at {@code guide-media/<id>.jpg}, served by {@link PublicFileController}; a video's own bytes are at
 * {@code guide-videos/<id>}, uploaded and played through presigned URLs.
 */
@Component
public class GuideMediaFiles {

    static final String PHOTO_PATH = "/api/public/files/guide-media/";
    public static final String VIDEO_PATH = "/api/public/files/guide-videos/";

    private final String publicBaseUrl;

    public GuideMediaFiles(AppProperties props) {
        String base = props.publicBaseUrl();
        this.publicBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public static String photoKey(UUID id) {
        return "guide-media/" + id + ".jpg";
    }

    public static String videoKey(UUID mediaId) {
        return "guide-videos/" + mediaId;
    }

    public String photoUrl(UUID id) {
        return id == null ? null : publicBaseUrl + PHOTO_PATH + id + ".jpg";
    }

    /** Redirects to a short-lived storage URL (GuideMediaController). */
    public String videoUrl(UUID mediaId) {
        return publicBaseUrl + VIDEO_PATH + mediaId;
    }
}
