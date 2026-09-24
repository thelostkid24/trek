package com.sahyatri.common.storage;

import com.sahyatri.common.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Where snow-report photos live in {@link FileStorage} and their public URL. Keyed by the report's id. */
@Component
public class SnowPhotoFiles {

    static final String URL_PATH = "/api/public/files/snow-reports/";

    private final String publicBaseUrl;

    public SnowPhotoFiles(AppProperties props) {
        String base = props.publicBaseUrl();
        this.publicBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public static String storageKey(UUID reportId) {
        return "snow-reports/" + reportId + ".jpg";
    }

    public String url(UUID reportId) {
        return publicBaseUrl + URL_PATH + reportId + ".jpg";
    }
}
