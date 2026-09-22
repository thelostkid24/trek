package com.sahyatri.catalog.dto;

import com.sahyatri.catalog.entity.TrackPhoto;
import com.sahyatri.common.storage.TrackPhotoFiles;

import java.util.UUID;

public record TrackPhotoResponse(UUID id, String url, String caption) {

    public static TrackPhotoResponse of(TrackPhoto p, TrackPhotoFiles files) {
        return new TrackPhotoResponse(p.getId(), files.url(p.getId()), p.getCaption());
    }
}
