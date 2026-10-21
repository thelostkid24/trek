package com.sahyatri.guide.dto;

import com.sahyatri.common.storage.GuideMediaFiles;
import com.sahyatri.guide.entity.GuideMedia;
import com.sahyatri.guide.entity.GuideMediaKind;
import com.sahyatri.guide.entity.GuideMediaStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * A photo or video on the guide's profile. {@code url} is the photo, or for a video a link that redirects to a
 * short-lived storage URL; {@code poster_url} is the video's still (null until one is uploaded).
 */
public record GuideMediaItem(UUID id, GuideMediaKind kind, String caption, int position, String url,
                             String posterUrl, Integer durationS, long sizeBytes, GuideMediaStatus status,
                             Instant createdAt) {

    public static GuideMediaItem of(GuideMedia m, GuideMediaFiles files) {
        boolean photo = m.getKind() == GuideMediaKind.PHOTO;
        return new GuideMediaItem(m.getId(), m.getKind(), m.getCaption(), m.getPosition(),
                photo ? files.photoUrl(m.getId()) : files.videoUrl(m.getId()),
                photo ? null : files.photoUrl(m.getPosterKey()),
                m.getDurationS(), m.getSizeBytes(), m.getStatus(), m.getCreatedAt());
    }
}
