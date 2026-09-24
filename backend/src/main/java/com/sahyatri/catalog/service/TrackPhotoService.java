package com.sahyatri.catalog.service;

import com.sahyatri.catalog.dto.TrackPhotoRequest;
import com.sahyatri.catalog.dto.TrackPhotoResponse;
import com.sahyatri.catalog.entity.Track;
import com.sahyatri.catalog.entity.TrackPhoto;
import com.sahyatri.catalog.repository.TrackPhotoRepository;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.common.storage.FileStorage;
import com.sahyatri.common.storage.Images;
import com.sahyatri.common.storage.TrackPhotoFiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.util.UUID;

/**
 * Photos from past runs of a trek, shown on the trek page. Uploads are re-encoded by {@link Images} and scaled so the
 * long edge is at most {@link #MAX_EDGE}. The file is written before the row, and removed after it, so a public
 * photo never points at a missing file.
 */
@Service
public class TrackPhotoService {

    static final int MAX_EDGE = 2000;
    static final int MAX_PHOTOS = 30;
    static final int MAX_CAPTION = 200;
    static final int MAX_PLACE = 100;

    private final TrackAdminService tracks;
    private final TrackPhotoRepository photos;
    private final FileStorage storage;
    private final TrackPhotoFiles files;

    public TrackPhotoService(TrackAdminService tracks, TrackPhotoRepository photos, FileStorage storage,
                             TrackPhotoFiles files) {
        this.tracks = tracks;
        this.photos = photos;
        this.storage = storage;
        this.files = files;
    }

    public TrackPhotoResponse upload(UUID trackId, MultipartFile file, String caption, String place,
                                     Integer dayNumber) {
        Track track = tracks.require(trackId);
        String trimmed = TrackAdminService.blankToNull(caption);
        if (trimmed != null && trimmed.length() > MAX_CAPTION) {
            throw ApiException.validation("caption", "must be at most " + MAX_CAPTION + " characters");
        }
        String trimmedPlace = TrackAdminService.blankToNull(place);
        if (trimmedPlace != null && trimmedPlace.length() > MAX_PLACE) {
            throw ApiException.validation("place", "must be at most " + MAX_PLACE + " characters");
        }
        checkDay(track, dayNumber);
        if (photos.countByTrackId(trackId) >= MAX_PHOTOS) {
            throw ApiException.conflict("TOO_MANY_PHOTOS", "A trek can have at most " + MAX_PHOTOS + " photos");
        }
        byte[] jpeg = toPhotoJpeg(Images.read(file));

        TrackPhoto photo = new TrackPhoto(track, trimmed, trimmedPlace, dayNumber);
        storage.put(TrackPhotoFiles.storageKey(photo.getId()), jpeg);
        try {
            photos.save(photo);
        } catch (RuntimeException e) {
            deleteQuietly(photo.getId());
            throw e;
        }
        return TrackPhotoResponse.of(photo, files);
    }

    @Transactional
    public TrackPhotoResponse describe(UUID trackId, UUID photoId, TrackPhotoRequest req) {
        TrackPhoto photo = require(trackId, photoId);
        checkDay(tracks.require(trackId), req.dayNumber());
        photo.describe(TrackAdminService.blankToNull(req.caption()), TrackAdminService.blankToNull(req.place()),
                req.dayNumber());
        return TrackPhotoResponse.of(photos.saveAndFlush(photo), files);
    }

    public void delete(UUID trackId, UUID photoId) {
        photos.delete(require(trackId, photoId));
        deleteQuietly(photoId);
    }

    private TrackPhoto require(UUID trackId, UUID photoId) {
        return photos.findByIdAndTrackId(photoId, trackId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PHOTO_NOT_FOUND", "Photo not found"));
    }

    private static void checkDay(Track track, Integer dayNumber) {
        if (dayNumber != null && (dayNumber < 1 || dayNumber > track.getDurationDays())) {
            throw ApiException.validation("day_number", "must be a day of the trek (1–" + track.getDurationDays() + ")");
        }
    }

    /** Re-encoded JPEG, long edge at most {@link #MAX_EDGE}. Also used for snow-report photos. */
    public static byte[] toPhotoJpeg(byte[] bytes) {
        BufferedImage source = Images.decode(bytes);
        int w = source.getWidth();
        int h = source.getHeight();
        double scale = Math.min(1.0, (double) MAX_EDGE / Math.max(w, h));
        int width = Math.max(1, (int) Math.round(w * scale));
        int height = Math.max(1, (int) Math.round(h * scale));
        return Images.encodeJpeg(Images.draw(source, 0, 0, w, h, width, height));
    }

    private void deleteQuietly(UUID photoId) {
        try {
            storage.delete(TrackPhotoFiles.storageKey(photoId));
        } catch (RuntimeException ignored) {
            // An orphaned file is harmless; nothing points at it.
        }
    }
}
