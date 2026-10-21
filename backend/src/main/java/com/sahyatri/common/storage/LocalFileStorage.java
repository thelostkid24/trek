package com.sahyatri.common.storage;

import com.sahyatri.common.config.AppProperties;
import com.sahyatri.common.config.AuthProperties;
import com.sahyatri.common.util.HashingUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Dev storage on local disk. Presigned URLs point back at {@link LocalFileController}, signed with the JWT secret,
 * so direct-to-storage uploads and Range playback work without S3. A file's content type sits beside it in
 * {@code <file>.type}.
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    static final String URL_PATH = "/api/public/local-files";
    private static final String TYPE_SUFFIX = ".type";

    private final Path root;
    private final String publicBaseUrl;
    private final String signingKey;

    public LocalFileStorage(AppProperties props, AuthProperties auth) {
        this.root = Path.of(props.storage().localDir()).toAbsolutePath().normalize();
        String base = props.publicBaseUrl();
        this.publicBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.signingKey = auth.jwtSecret();
    }

    @Override
    public void put(String key, byte[] content) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            // Write then move so a reader never sees a half-written file.
            Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            Files.write(temp, content);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store " + key, e);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        Path target = resolve(key);
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(target));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Path target = resolve(key);
            Files.deleteIfExists(target);
            Files.deleteIfExists(typeFile(target));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete " + key, e);
        }
    }

    @Override
    public PresignedUpload presignUpload(String key, String contentType, long sizeBytes, Duration ttl) {
        resolve(key);
        long expires = Instant.now().plus(ttl).getEpochSecond();
        String url = UriComponentsBuilder.fromUriString(publicBaseUrl + URL_PATH)
                .queryParam("key", key)
                .queryParam("type", contentType)
                .queryParam("size", sizeBytes)
                .queryParam("expires", expires)
                .queryParam("sig", sign("PUT", key, contentType, sizeBytes, expires))
                .encode().toUriString();
        return new PresignedUpload(url, Map.of("Content-Type", contentType));
    }

    @Override
    public Optional<StoredObject> head(String key) {
        Path target = resolve(key);
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            Path type = typeFile(target);
            String contentType = Files.isRegularFile(type)
                    ? Files.readString(type, StandardCharsets.UTF_8) : "application/octet-stream";
            return Optional.of(new StoredObject(Files.size(target), contentType));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + key, e);
        }
    }

    @Override
    public String presignDownload(String key, Duration ttl) {
        resolve(key);
        long expires = Instant.now().plus(ttl).getEpochSecond();
        return UriComponentsBuilder.fromUriString(publicBaseUrl + URL_PATH)
                .queryParam("key", key)
                .queryParam("expires", expires)
                .queryParam("sig", sign("GET", key, "", 0, expires))
                .encode().toUriString();
    }

    /** True when {@code sig} is ours for these values and hasn't expired. */
    boolean verify(String method, String key, String contentType, long sizeBytes, long expires, String sig) {
        return sig != null && Instant.now().getEpochSecond() <= expires
                && HashingUtils.constantTimeEquals(sign(method, key, contentType, sizeBytes, expires), sig);
    }

    /**
     * Streams a presigned upload to disk. False (and nothing stored) unless exactly {@code sizeBytes} arrive, the
     * same rule S3 applies to a signed Content-Length.
     */
    boolean write(String key, String contentType, long sizeBytes, InputStream body) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            try {
                long copied = 0;
                byte[] buf = new byte[64 * 1024];
                try (OutputStream out = Files.newOutputStream(temp)) {
                    for (int n; (n = body.read(buf)) != -1; ) {
                        copied += n;
                        if (copied > sizeBytes) {
                            return false;
                        }
                        out.write(buf, 0, n);
                    }
                }
                if (copied != sizeBytes) {
                    return false;
                }
                Files.writeString(typeFile(target), contentType, StandardCharsets.UTF_8);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return true;
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store " + key, e);
        }
    }

    Path path(String key) {
        return resolve(key);
    }

    private String sign(String method, String key, String contentType, long sizeBytes, long expires) {
        return HashingUtils.hmacSha256Hex(signingKey,
                "local-file\n" + method + "\n" + key + "\n" + contentType + "\n" + sizeBytes + "\n" + expires);
    }

    private static Path typeFile(Path target) {
        return target.resolveSibling(target.getFileName() + TYPE_SUFFIX);
    }

    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IllegalArgumentException("Invalid storage key: " + key);
        }
        return target;
    }
}
