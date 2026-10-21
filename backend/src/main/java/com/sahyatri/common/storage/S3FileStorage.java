package com.sahyatri.common.storage;

import com.sahyatri.common.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Production storage: one private S3 bucket. Credentials and region come from the ECS task (default chain).
 * Presigned uploads and downloads need the bucket's CORS to allow PUT and GET from the site (docs/DEPLOY.md).
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3FileStorage implements FileStorage {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public S3FileStorage(AppProperties props) {
        this.bucket = props.storage().s3Bucket();
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("S3_UPLOADS_BUCKET must be set when STORAGE_TYPE=s3");
        }
        this.s3 = S3Client.create();
        this.presigner = S3Presigner.create();
    }

    @Override
    public void put(String key, byte[] content) {
        s3.putObject(b -> b.bucket(bucket).key(key), RequestBody.fromBytes(content));
    }

    @Override
    public Optional<byte[]> get(String key) {
        try {
            return Optional.of(s3.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(b -> b.bucket(bucket).key(key));
    }

    /** Content-Type and Content-Length are signed, so S3 refuses any other type or size. */
    @Override
    public PresignedUpload presignUpload(String key, String contentType, long sizeBytes, Duration ttl) {
        String url = presigner.presignPutObject(p -> p.signatureDuration(ttl)
                        .putObjectRequest(r -> r.bucket(bucket).key(key).contentType(contentType).contentLength(sizeBytes)))
                .url().toString();
        // The browser sets Content-Length itself (it may not set it by hand).
        return new PresignedUpload(url, Map.of("Content-Type", contentType));
    }

    @Override
    public Optional<StoredObject> head(String key) {
        try {
            HeadObjectResponse head = s3.headObject(b -> b.bucket(bucket).key(key));
            return Optional.of(new StoredObject(head.contentLength(), head.contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public String presignDownload(String key, Duration ttl) {
        return presigner.presignGetObject(p -> p.signatureDuration(ttl)
                .getObjectRequest(r -> r.bucket(bucket).key(key))).url().toString();
    }
}
