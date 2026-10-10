package com.sahyatri.common.storage;

import com.sahyatri.common.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Where blog photos live in {@link FileStorage} and their public URL. Keyed by the photo's id. */
@Component
public class BlogPhotoFiles {

    static final String URL_PATH = "/api/public/files/blog-photos/";

    private final String publicBaseUrl;

    public BlogPhotoFiles(AppProperties props) {
        String base = props.publicBaseUrl();
        this.publicBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public static String storageKey(UUID photoId) {
        return "blog-photos/" + photoId + ".jpg";
    }

    public String url(UUID photoId) {
        return publicBaseUrl + URL_PATH + photoId + ".jpg";
    }
}
