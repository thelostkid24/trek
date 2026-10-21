package com.sahyatri.common.storage;

import com.sahyatri.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Dev stand-in for S3 presigned URLs ({@link LocalFileStorage}). Every request carries a signature over what it may
 * do; there is no JWT. GET returns a {@link Resource}, which Spring serves with Range support (206 partial content).
 */
@RestController
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileController {

    private final LocalFileStorage storage;

    public LocalFileController(LocalFileStorage storage) {
        this.storage = storage;
    }

    @PutMapping(LocalFileStorage.URL_PATH)
    public ResponseEntity<Void> upload(@RequestParam String key, @RequestParam String type, @RequestParam long size,
                                       @RequestParam long expires, @RequestParam String sig,
                                       @RequestHeader(name = "Content-Type", required = false) String contentType,
                                       HttpServletRequest request) throws IOException {
        if (!storage.verify("PUT", key, type, size, expires, sig)) {
            throw ApiException.forbidden("Upload link is invalid or expired");
        }
        if (!sameType(type, contentType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONTENT_TYPE_MISMATCH", "Content-Type must be " + type);
        }
        if (!storage.write(key, type, size, request.getInputStream())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SIZE_MISMATCH", "Upload must be exactly " + size + " bytes");
        }
        return ResponseEntity.ok().build();
    }

    /** Ignores parameters such as charset, as S3 compares the signed header. */
    private static boolean sameType(String expected, String header) {
        try {
            return header != null && MediaType.parseMediaType(expected).equalsTypeAndSubtype(MediaType.parseMediaType(header));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @GetMapping(LocalFileStorage.URL_PATH)
    public ResponseEntity<Resource> download(@RequestParam String key, @RequestParam long expires,
                                             @RequestParam String sig) {
        if (!storage.verify("GET", key, "", 0, expires, sig)) {
            throw ApiException.forbidden("Link is invalid or expired");
        }
        FileStorage.StoredObject head = storage.head(key).orElseThrow(() -> ApiException.notFound("File not found"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(head.contentType()))
                .cacheControl(CacheControl.noStore())
                .body(new FileSystemResource(storage.path(key)));
    }
}
