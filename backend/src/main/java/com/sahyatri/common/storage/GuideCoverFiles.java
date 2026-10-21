package com.sahyatri.common.storage;

import com.sahyatri.common.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Where guide cover photos live in {@link FileStorage} and their public URL. Keyed by a new id on every upload. */
@Component
public class GuideCoverFiles {

    static final String URL_PATH = "/api/public/files/guide-covers/";

    private final String publicBaseUrl;

    public GuideCoverFiles(AppProperties props) {
        String base = props.publicBaseUrl();
        this.publicBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public static String storageKey(UUID coverId) {
        return "guide-covers/" + coverId + ".jpg";
    }

    public String url(String coverKey) {
        return coverKey == null ? null : publicBaseUrl + URL_PATH + coverKey + ".jpg";
    }
}
