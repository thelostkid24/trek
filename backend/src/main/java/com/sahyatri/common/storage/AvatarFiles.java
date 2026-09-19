package com.sahyatri.common.storage;

import com.sahyatri.common.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Where profile photos live in {@link FileStorage} and the public URL they are served from. */
@Component
public class AvatarFiles {

    static final String URL_PATH = "/api/public/files/avatars/";

    private final String publicBaseUrl;

    public AvatarFiles(AppProperties props) {
        this.publicBaseUrl = stripTrailingSlash(props.publicBaseUrl());
    }

    public static String storageKey(UUID avatarKey) {
        return "avatars/" + avatarKey + ".jpg";
    }

    /** Null when the user has no photo. */
    public String url(String avatarKey) {
        return avatarKey == null ? null : publicBaseUrl + URL_PATH + avatarKey + ".jpg";
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
