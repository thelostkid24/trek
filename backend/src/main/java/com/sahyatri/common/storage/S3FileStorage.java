package com.sahyatri.common.storage;

import com.sahyatri.common.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.util.Optional;

/** Production storage: one private S3 bucket. Credentials and region come from the ECS task (default chain). */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3FileStorage implements FileStorage {

    private final S3Client s3;
    private final String bucket;

    public S3FileStorage(AppProperties props) {
        this.bucket = props.storage().s3Bucket();
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("S3_UPLOADS_BUCKET must be set when STORAGE_TYPE=s3");
        }
        this.s3 = S3Client.create();
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
}
