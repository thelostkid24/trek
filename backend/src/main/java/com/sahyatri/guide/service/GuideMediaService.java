package com.sahyatri.guide.service;

import com.sahyatri.catalog.service.TrackPhotoService;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.common.storage.FileStorage;
import com.sahyatri.common.storage.GuideMediaFiles;
import com.sahyatri.common.storage.Images;
import com.sahyatri.guide.dto.GuideMediaItem;
import com.sahyatri.guide.dto.GuideMediaList;
import com.sahyatri.guide.dto.MediaCaptionRequest;
import com.sahyatri.guide.dto.MediaOrderRequest;
import com.sahyatri.guide.dto.YouTubeVideoRequest;
import com.sahyatri.guide.entity.GuideMedia;
import com.sahyatri.guide.entity.GuideMediaKind;
import com.sahyatri.guide.repository.GuideMediaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Photos and YouTube videos on the guide profile (§7.21.4). Photos come through us and are re-encoded like trek
 * photos; the file is written before its row and removed after it. A video is only a YouTube video id: we store no
 * video bytes, and the page plays it embedded from youtube-nocookie.com.
 */
@Service
public class GuideMediaService {

    static final int MAX_ITEMS = 20;
    static final int MAX_CAPTION = 120;

    private final GuideMediaRepository media;
    private final GuideSlugService slugs;
    private final FileStorage storage;
    private final GuideMediaFiles files;

    public GuideMediaService(GuideMediaRepository media, GuideSlugService slugs, FileStorage storage,
                             GuideMediaFiles files) {
        this.media = media;
        this.slugs = slugs;
        this.storage = storage;
        this.files = files;
    }

    @Transactional(readOnly = true)
    public GuideMediaList list(UUID guideId) {
        return toList(media.findByGuideIdOrderByPositionAscCreatedAtAsc(guideId));
    }

    /** What the public page shows, in the guide's order. */
    @Transactional(readOnly = true)
    public GuideMediaList publicList(String key) {
        UUID guideId = slugs.resolve(key)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GUIDE_NOT_FOUND", "Guide not found"));
        return list(guideId);
    }

    public GuideMediaItem uploadPhoto(UUID guideId, MultipartFile file, String caption) {
        String trimmed = caption(caption);
        checkRoom(guideId);
        byte[] jpeg = TrackPhotoService.toPhotoJpeg(Images.read(file));
        GuideMedia photo = GuideMedia.photo(guideId, trimmed, media.nextPosition(guideId));
        storage.put(GuideMediaFiles.photoKey(photo.getId()), jpeg);
        try {
            media.saveAndFlush(photo);
        } catch (RuntimeException e) {
            deleteQuietly(GuideMediaFiles.photoKey(photo.getId()));
            throw e;
        }
        return GuideMediaItem.of(photo, files);
    }

    /** A YouTube video, from any of the usual link shapes; the same video twice is refused. */
    @Transactional
    public GuideMediaItem addYouTube(UUID guideId, YouTubeVideoRequest req) {
        String youtubeId = YouTubeLinks.videoId(req.url()).orElseThrow(() ->
                new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_VIDEO_LINK", "Paste a link to a YouTube video"));
        String trimmed = caption(req.caption());
        if (media.existsByGuideIdAndYoutubeId(guideId, youtubeId)) {
            throw ApiException.conflict("VIDEO_ALREADY_ADDED", "That video is already on your profile");
        }
        checkRoom(guideId);
        GuideMedia video = GuideMedia.youtube(guideId, trimmed, media.nextPosition(guideId), youtubeId);
        return GuideMediaItem.of(media.saveAndFlush(video), files);
    }

    @Transactional
    public GuideMediaItem updateCaption(UUID guideId, UUID id, MediaCaptionRequest req) {
        GuideMedia item = require(guideId, id);
        item.setCaption(caption(req.caption()));
        return GuideMediaItem.of(media.saveAndFlush(item), files);
    }

    /** Replaces the order; {@code ids} must be exactly the guide's items. */
    @Transactional
    public GuideMediaList reorder(UUID guideId, MediaOrderRequest req) {
        List<GuideMedia> items = media.findByGuideIdOrderByPositionAscCreatedAtAsc(guideId);
        Set<UUID> have = new HashSet<>();
        items.forEach(m -> have.add(m.getId()));
        if (req.ids().size() != items.size() || !have.equals(new HashSet<>(req.ids()))) {
            throw ApiException.validation("ids", "must list each of your photos and videos once");
        }
        Map<UUID, GuideMedia> byId = new HashMap<>();
        items.forEach(m -> byId.put(m.getId(), m));
        for (int i = 0; i < req.ids().size(); i++) {
            byId.get(req.ids().get(i)).setPosition(i);
        }
        media.saveAllAndFlush(items);
        return toList(media.findByGuideIdOrderByPositionAscCreatedAtAsc(guideId));
    }

    public void delete(UUID guideId, UUID id) {
        GuideMedia item = require(guideId, id);
        media.delete(item);
        if (item.getKind() == GuideMediaKind.PHOTO) {
            deleteQuietly(GuideMediaFiles.photoKey(item.getId()));
        }
    }

    private void checkRoom(UUID guideId) {
        if (media.countByGuideId(guideId) >= MAX_ITEMS) {
            throw ApiException.conflict("TOO_MANY_MEDIA",
                    "A profile can have at most " + MAX_ITEMS + " photos and videos");
        }
    }

    private GuideMedia require(UUID guideId, UUID id) {
        return media.findByIdAndGuideId(id, guideId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Photo or video not found"));
    }

    private static String caption(String caption) {
        String trimmed = caption == null || caption.isBlank() ? null : caption.trim();
        if (trimmed != null && trimmed.length() > MAX_CAPTION) {
            throw ApiException.validation("caption", "must be at most " + MAX_CAPTION + " characters");
        }
        return trimmed;
    }

    private GuideMediaList toList(List<GuideMedia> items) {
        return new GuideMediaList(items.stream().map(m -> GuideMediaItem.of(m, files)).toList());
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException ignored) {
            // An orphaned file is harmless; nothing points at it.
        }
    }
}
