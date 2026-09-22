package com.sahyatri.common.storage;

import com.sahyatri.common.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Where trek photos live in {@link FileStorage} and the public URL they are served from. Keyed by the photo's id. */
@Component
public class TrackPhotoFiles {

    static final String URL_PATH = "/api/public/files/track-photos/";

    private final String publicBaseUrl;

    public TrackPhotoFiles(AppProperties props) {
        String base = props.publicBaseUrl();
        this.publicBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public static String storageKey(UUID photoId) {
        return "track-photos/" + photoId + ".jpg";
    }

    public String url(UUID photoId) {
        return publicBaseUrl + URL_PATH + photoId + ".jpg";
    }
}
