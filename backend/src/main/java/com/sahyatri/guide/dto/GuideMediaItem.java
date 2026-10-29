package com.sahyatri.guide.dto;

import com.sahyatri.common.storage.GuideMediaFiles;
import com.sahyatri.guide.entity.GuideMedia;
import com.sahyatri.guide.entity.GuideMediaKind;

import java.time.Instant;
import java.util.UUID;

/**
 * A photo or YouTube video on the guide's profile. A photo has {@code url} and a small {@code thumbnail_url}; a video
 * has {@code youtube_id} and {@code thumbnail_url} (YouTube's still), and plays embedded from youtube-nocookie.com.
 */
public record GuideMediaItem(UUID id, GuideMediaKind kind, String caption, int position, String url,
                             String youtubeId, String thumbnailUrl, Instant createdAt) {

    public static GuideMediaItem of(GuideMedia m, GuideMediaFiles files) {
        boolean photo = m.getKind() == GuideMediaKind.PHOTO;
        return new GuideMediaItem(m.getId(), m.getKind(), m.getCaption(), m.getPosition(),
                photo ? files.photoUrl(m.getId()) : null, m.getYoutubeId(),
                photo ? files.thumbUrl(m.getId()) : "https://i.ytimg.com/vi/" + m.getYoutubeId() + "/hqdefault.jpg",
                m.getCreatedAt());
    }
}
