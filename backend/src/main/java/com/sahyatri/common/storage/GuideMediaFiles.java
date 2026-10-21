package com.sahyatri.common.storage;

import com.sahyatri.common.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Where guide profile photos live in {@link FileStorage} (§7.21.4): re-encoded JPEGs at {@code guide-media/<id>.jpg},
 * served by {@link PublicFileController}. Videos are YouTube links; we store no video files.
 */
@Component
public class GuideMediaFiles {

    static final String PHOTO_PATH = "/api/public/files/guide-media/";

    private final String publicBaseUrl;

    public GuideMediaFiles(AppProperties props) {
        String base = props.publicBaseUrl();
        this.publicBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public static String photoKey(UUID id) {
        return "guide-media/" + id + ".jpg";
    }

    public String photoUrl(UUID id) {
        return publicBaseUrl + PHOTO_PATH + id + ".jpg";
    }
}
