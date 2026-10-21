package com.sahyatri.guide.service;

import com.sahyatri.catalog.service.TrackPhotoService;
import com.sahyatri.common.config.GuideMediaProperties;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.common.storage.FileStorage;
import com.sahyatri.common.storage.GuideMediaFiles;
import com.sahyatri.common.storage.Images;
import com.sahyatri.guide.dto.GuideMediaItem;
import com.sahyatri.guide.dto.GuideMediaList;
import com.sahyatri.guide.dto.MediaCaptionRequest;
import com.sahyatri.guide.dto.MediaOrderRequest;
import com.sahyatri.guide.dto.VideoUploadRequest;
import com.sahyatri.guide.dto.VideoUploadResponse;
import com.sahyatri.guide.entity.GuideMedia;
import com.sahyatri.guide.entity.GuideMediaKind;
import com.sahyatri.guide.entity.GuideMediaStatus;
import com.sahyatri.guide.repository.GuideMediaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Photos and short videos on the guide profile (§7.21.4). Photos (and video posters) come through us and are
 * re-encoded like trek photos. Video bytes never do: the browser PUTs them straight to storage by a presigned URL,
 * then confirms, and we check what arrived with a HEAD. No transcoding, so only MP4 and MOV are taken.
 * Files are written before their row and removed after it, so a READY item never points at a missing file.
 */
@Service
public class GuideMediaService {

    static final int MAX_ITEMS = 20;
    static final int MAX_CAPTION = 120;
    static final int MAX_DURATION_S = 90;
    static final long MAX_VIDEO_BYTES = 100L * 1024 * 1024;
    static final Set<String> VIDEO_TYPES = Set.of("video/mp4", "video/quicktime");

    private final GuideMediaRepository media;
    private final GuideSlugService slugs;
    private final FileStorage storage;
    private final GuideMediaFiles files;
    private final GuideMediaProperties props;

    public GuideMediaService(GuideMediaRepository media, GuideSlugService slugs, FileStorage storage,
                             GuideMediaFiles files, GuideMediaProperties props) {
        this.media = media;
        this.slugs = slugs;
        this.storage = storage;
        this.files = files;
        this.props = props;
    }

    /** Everything the guide has, pending videos included. */
    @Transactional(readOnly = true)
    public GuideMediaList list(UUID guideId) {
        return toList(media.findByGuideIdOrderByPositionAscCreatedAtAsc(guideId));
    }

    /** What the public page shows: ready items in the guide's order. */
    @Transactional(readOnly = true)
    public GuideMediaList publicList(String key) {
        UUID guideId = slugs.resolve(key)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GUIDE_NOT_FOUND", "Guide not found"));
        return toList(media.findByGuideIdAndStatusOrderByPositionAscCreatedAtAsc(guideId, GuideMediaStatus.READY));
    }

    public GuideMediaItem uploadPhoto(UUID guideId, MultipartFile file, String caption) {
        String trimmed = caption(caption);
        checkRoom(guideId);
        byte[] jpeg = TrackPhotoService.toPhotoJpeg(Images.read(file));
        GuideMedia photo = GuideMedia.photo(guideId, trimmed, media.nextPosition(guideId), jpeg.length);
        storage.put(GuideMediaFiles.photoKey(photo.getId()), jpeg);
        try {
            media.saveAndFlush(photo);
        } catch (RuntimeException e) {
            deleteQuietly(GuideMediaFiles.photoKey(photo.getId()));
            throw e;
        }
        return GuideMediaItem.of(photo, files);
    }

    /** A PENDING row and a presigned PUT URL for exactly this size and type. */
    @Transactional
    public VideoUploadResponse startVideo(UUID guideId, VideoUploadRequest req) {
        String type = req.contentType().trim().toLowerCase(Locale.ROOT);
        if (!VIDEO_TYPES.contains(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_VIDEO", "Video must be MP4 or MOV (H.264)");
        }
        if (req.sizeBytes() > MAX_VIDEO_BYTES) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", "Video must be 100 MB or smaller");
        }
        if (req.durationS() > MAX_DURATION_S) {
            throw ApiException.validation("duration_s", "must be at most " + MAX_DURATION_S + " seconds");
        }
        String trimmed = caption(req.caption());
        checkRoom(guideId);
        int seconds = (int) Math.max(1, Math.ceil(req.durationS()));
        GuideMedia video = media.saveAndFlush(GuideMedia.pendingVideo(guideId, trimmed, media.nextPosition(guideId),
                seconds, req.sizeBytes()));
        FileStorage.PresignedUpload upload = storage.presignUpload(GuideMediaFiles.videoKey(video.getId()), type,
                req.sizeBytes(), props.uploadTtl());
        return new VideoUploadResponse(video.getId(), upload.url(), upload.headers());
    }

    /**
     * Makes the video public once its file is in storage with the size and a type we take. A file that doesn't
     * match is removed with its row; a missing one can be retried until the cleanup job takes the row.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public GuideMediaItem completeVideo(UUID guideId, UUID id) {
        GuideMedia video = requireVideo(guideId, id);
        if (video.getStatus() == GuideMediaStatus.READY) {
            return GuideMediaItem.of(video, files);
        }
        String key = GuideMediaFiles.videoKey(id);
        FileStorage.StoredObject stored = storage.head(key).orElseThrow(() ->
                ApiException.conflict("UPLOAD_MISSING", "The video hasn't finished uploading"));
        String type = stored.contentType() == null ? "" : stored.contentType().toLowerCase(Locale.ROOT);
        if (stored.sizeBytes() != video.getSizeBytes() || stored.sizeBytes() > MAX_VIDEO_BYTES
                || !VIDEO_TYPES.contains(type)) {
            media.delete(video);
            media.flush();
            removeFiles(video);
            throw new ApiException(HttpStatus.BAD_REQUEST, "UPLOAD_INVALID",
                    "The uploaded file isn't the video that was started; add it again");
        }
        video.ready();
        return GuideMediaItem.of(media.saveAndFlush(video), files);
    }

    /** The video's still, a frame the browser grabbed. Replaces any earlier one. */
    public GuideMediaItem uploadPoster(UUID guideId, UUID id, MultipartFile file) {
        requireVideo(guideId, id);
        byte[] jpeg = TrackPhotoService.toPhotoJpeg(Images.read(file));
        UUID posterKey = UUID.randomUUID();
        storage.put(GuideMediaFiles.photoKey(posterKey), jpeg);
        GuideMedia video;
        UUID previous;
        try {
            video = requireVideo(guideId, id);
            previous = video.getPosterKey();
            video.setPosterKey(posterKey);
            video = media.saveAndFlush(video);
        } catch (RuntimeException e) {
            deleteQuietly(GuideMediaFiles.photoKey(posterKey));
            throw e;
        }
        if (previous != null) {
            deleteQuietly(GuideMediaFiles.photoKey(previous));
        }
        return GuideMediaItem.of(video, files);
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
        removeFiles(item);
    }

    /** Where a ready video plays from right now; the public route redirects there. */
    @Transactional(readOnly = true)
    public String playUrl(UUID id) {
        GuideMedia video = media.findByIdAndStatus(id, GuideMediaStatus.READY)
                .filter(m -> m.getKind() == GuideMediaKind.VIDEO)
                .orElseThrow(() -> ApiException.notFound("Video not found"));
        return storage.presignDownload(GuideMediaFiles.videoKey(video.getId()), props.playTtl());
    }

    /** Called by the cleanup job after the row is gone. */
    void removeFiles(GuideMedia item) {
        if (item.getKind() == GuideMediaKind.PHOTO) {
            deleteQuietly(GuideMediaFiles.photoKey(item.getId()));
        } else {
            deleteQuietly(GuideMediaFiles.videoKey(item.getId()));
            if (item.getPosterKey() != null) {
                deleteQuietly(GuideMediaFiles.photoKey(item.getPosterKey()));
            }
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

    private GuideMedia requireVideo(UUID guideId, UUID id) {
        GuideMedia item = require(guideId, id);
        if (item.getKind() != GuideMediaKind.VIDEO) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Video not found");
        }
        return item;
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
