package com.sahyatri.common.storage;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Stores binary files by key (e.g. `avatars/<uuid>.jpg`): local disk in dev, one private S3 bucket in production.
 * Small files go through {@link #put}; large ones (guide videos) go from the browser straight to storage by a
 * presigned URL and are read back the same way, so their bytes never pass through the backend.
 */
public interface FileStorage {

    void put(String key, byte[] content);

    Optional<byte[]> get(String key);

    void delete(String key);

    /** A URL the browser PUTs exactly {@code sizeBytes} bytes of {@code contentType} to, valid for {@code ttl}. */
    PresignedUpload presignUpload(String key, String contentType, long sizeBytes, Duration ttl);

    /** Size and type of a stored file, without reading it; empty when there is none. */
    Optional<StoredObject> head(String key);

    /** A URL the browser GETs the file from for {@code ttl}; Range requests work, so videos seek. */
    String presignDownload(String key, Duration ttl);

    /** @param headers what the browser must send with the PUT (e.g. Content-Type), as signed */
    record PresignedUpload(String url, Map<String, String> headers) {
    }

    record StoredObject(long sizeBytes, String contentType) {
    }
}
