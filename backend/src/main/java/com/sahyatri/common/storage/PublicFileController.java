package com.sahyatri.common.storage;

import com.sahyatri.common.exception.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/** Serves public uploads. Keys are random UUIDs that change on every upload, so responses cache forever. */
@RestController
public class PublicFileController {

    private final FileStorage storage;

    public PublicFileController(FileStorage storage) {
        this.storage = storage;
    }

    @GetMapping(AvatarFiles.URL_PATH + "{id:[0-9a-fA-F-]{36}}.jpg")
    public ResponseEntity<byte[]> avatar(@PathVariable String id) {
        return serve(AvatarFiles.storageKey(parse(id)));
    }

    @GetMapping(TrackPhotoFiles.URL_PATH + "{id:[0-9a-fA-F-]{36}}.jpg")
    public ResponseEntity<byte[]> trackPhoto(@PathVariable String id) {
        return serve(TrackPhotoFiles.storageKey(parse(id)));
    }

    @GetMapping(SnowPhotoFiles.URL_PATH + "{id:[0-9a-fA-F-]{36}}.jpg")
    public ResponseEntity<byte[]> snowPhoto(@PathVariable String id) {
        return serve(SnowPhotoFiles.storageKey(parse(id)));
    }

    @GetMapping(BlogPhotoFiles.URL_PATH + "{id:[0-9a-fA-F-]{36}}.jpg")
    public ResponseEntity<byte[]> blogPhoto(@PathVariable String id) {
        return serve(BlogPhotoFiles.storageKey(parse(id)));
    }

    @GetMapping(GuideCoverFiles.URL_PATH + "{id:[0-9a-fA-F-]{36}}.jpg")
    public ResponseEntity<byte[]> guideCover(@PathVariable String id) {
        return serve(GuideCoverFiles.storageKey(parse(id)));
    }

    @GetMapping(GuideMediaFiles.PHOTO_PATH + "{id:[0-9a-fA-F-]{36}}.jpg")
    public ResponseEntity<byte[]> guideMedia(@PathVariable String id) {
        return serve(GuideMediaFiles.photoKey(parse(id)));
    }

    /** The gallery strip's small copy; made from the photo on its first request and kept, so older photos get one too. */
    @GetMapping(GuideMediaFiles.PHOTO_PATH + "{id:[0-9a-fA-F-]{36}}-thumb.jpg")
    public ResponseEntity<byte[]> guideMediaThumb(@PathVariable String id) {
        UUID photo = parse(id);
        byte[] thumb = storage.get(GuideMediaFiles.thumbKey(photo)).orElseGet(() -> {
            byte[] full = storage.get(GuideMediaFiles.photoKey(photo))
                    .orElseThrow(() -> ApiException.notFound("File not found"));
            byte[] small = Images.shrink(full, GuideMediaFiles.THUMB_EDGE);
            storage.put(GuideMediaFiles.thumbKey(photo), small);
            return small;
        });
        return jpeg(thumb);
    }

    private ResponseEntity<byte[]> serve(String key) {
        return jpeg(storage.get(key).orElseThrow(() -> ApiException.notFound("File not found")));
    }

    private static ResponseEntity<byte[]> jpeg(byte[] content) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(content);
    }

    private static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("File not found");
        }
    }
}
