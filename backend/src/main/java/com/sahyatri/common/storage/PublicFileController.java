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

    private ResponseEntity<byte[]> serve(String key) {
        byte[] content = storage.get(key).orElseThrow(() -> ApiException.notFound("File not found"));
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
