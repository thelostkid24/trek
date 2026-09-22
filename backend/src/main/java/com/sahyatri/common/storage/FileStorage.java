package com.sahyatri.common.storage;

import java.util.Optional;

/** Stores small binary files by key (e.g. `avatars/<uuid>.jpg`). V1 writes to local disk; S3 can replace it later. */
public interface FileStorage {

    void put(String key, byte[] content);

    Optional<byte[]> get(String key);

    void delete(String key);
}
