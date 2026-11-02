package com.meuquarto.shared.storage;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * {@link ObjectStorage} over the S3 API (AWS SDK v2) with path-style addressing, which every
 * S3-compatible server accepts. The client is built on first use, so the API starts (and everything
 * that does not touch files works) even when storage is not configured. The bucket is created the
 * first time it is needed if it is missing, and is private: no policy or ACL is ever set.
 */
@Component
@EnableConfigurationProperties(StorageProperties.class)
class S3ObjectStorage implements ObjectStorage {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration ATTEMPT_TIMEOUT = Duration.ofSeconds(5);

    private final StorageProperties properties;
    private volatile S3Client client;
    private volatile boolean bucketReady;

    S3ObjectStorage(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        try {
            S3Client s3 = client();
            ensureBucket(s3);
            s3.putObject(PutObjectRequest.builder().bucket(properties.bucket()).key(key).contentType(contentType)
                    .build(), RequestBody.fromBytes(content));
        } catch (SdkException | IllegalArgumentException e) {
            throw new StorageUnavailableException(e);
        }
    }

    @Override
    public Optional<StoredObject> get(String key) {
        try {
            var response = client().getObjectAsBytes(
                    GetObjectRequest.builder().bucket(properties.bucket()).key(key).build());
            return Optional.of(new StoredObject(response.asByteArray(), response.response().contentType()));
        } catch (NoSuchKeyException | NoSuchBucketException e) {
            return Optional.empty();
        } catch (SdkException | IllegalArgumentException e) {
            throw new StorageUnavailableException(e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client().deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build());
        } catch (NoSuchKeyException | NoSuchBucketException e) {
            // already gone
        } catch (SdkException | IllegalArgumentException e) {
            throw new StorageUnavailableException(e);
        }
    }

    private void ensureBucket(S3Client s3) {
        if (bucketReady) {
            return;
        }
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build());
        } catch (NoSuchBucketException missing) {
            create(s3);
        } catch (S3Exception e) {
            if (e.statusCode() != 404) {
                throw e;
            }
            create(s3);
        }
        bucketReady = true;
    }

    private void create(S3Client s3) {
        s3.createBucket(CreateBucketRequest.builder().bucket(properties.bucket()).build());
    }

    private S3Client client() {
        S3Client current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    current = build();
                    client = current;
                }
            }
        }
        return current;
    }

    private S3Client build() {
        return S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                // Checksums only where S3 requires them: other S3-compatible servers do not all accept the new defaults.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(3)).socketTimeout(ATTEMPT_TIMEOUT))
                .overrideConfiguration(c -> c.apiCallTimeout(CALL_TIMEOUT).apiCallAttemptTimeout(ATTEMPT_TIMEOUT))
                .build();
    }
}
