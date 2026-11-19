package com.siose.ff.base.storage;

import com.siose.ff.base.storage.StorageObjectDescriptor.StorageMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Object storage over the S3 API ({@code ff.storage.provider=s3}).
 *
 * <p>Works with any S3-compatible server: point {@code ff.storage.s3.endpoint} at RustFS
 * locally, or leave it empty for AWS S3. Without an access key the AWS default credential chain
 * is used (environment, profile, instance or pod role), so no secret has to be in configuration.
 *
 * <p>The bucket is created when it is first needed: before the first {@link #store} and before the first
 * upload link, since a client's PUT through a presigned link cannot create it.
 *
 * <p>A store that does not answer, or answers with an error other than "not found", is reported as
 * {@link ObjectStoreUnavailableException} (503), with the SDK's exception as the cause for the log.
 * Signing a link needs no call to the store.
 */
public class S3ObjectStorageAdapter implements ObjectStoragePort, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectStorageAdapter.class);

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;
    private volatile boolean bucketReady;

    S3ObjectStorageAdapter(S3Client s3, S3Presigner presigner, String bucket) {
        this.s3 = Objects.requireNonNull(s3);
        this.presigner = Objects.requireNonNull(presigner);
        this.bucket = ObjectKeys.segment(bucket, "bucket");
    }

    public static S3ObjectStorageAdapter create(ObjectStorageProperties properties) {
        ObjectStorageProperties.S3 settings = properties.getS3();
        Region region = Region.of(settings.getRegion());
        AwsCredentialsProvider credentials = settings.hasStaticCredentials()
                ? StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(settings.getAccessKey(), settings.getSecretKey()))
                : DefaultCredentialsProvider.builder().build();
        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(settings.isPathStyleAccess())
                .build();

        var clientBuilder = S3Client.builder()
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Config)
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(settings.getTimeout())
                        .build());
        var presignerBuilder = S3Presigner.builder()
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Config);
        // The application talks to the endpoint; links are signed for the address clients use.
        if (settings.getEndpoint() != null) {
            clientBuilder.endpointOverride(settings.getEndpoint());
        }
        if (settings.linkEndpoint() != null) {
            presignerBuilder.endpointOverride(settings.linkEndpoint());
        }
        log.info("Object storage: S3 API at {}, links for {}, bucket '{}'",
                settings.getEndpoint() != null ? settings.getEndpoint() : "AWS default endpoint",
                settings.linkEndpoint() != null ? settings.linkEndpoint() : "AWS default endpoint",
                properties.getBucket());
        return new S3ObjectStorageAdapter(clientBuilder.build(), presignerBuilder.build(), properties.getBucket());
    }

    @Override
    public StorageObjectDescriptor registerDocumentReference(
            String tenantId, String category, String referenceId, String fileName, String contentType) {
        return ObjectKeys.describe(bucket, tenantId, category, referenceId, fileName, contentType,
                StorageMode.REFERENCE_ONLY);
    }

    @Override
    public StorageObjectDescriptor store(
            String tenantId, String category, String referenceId, String fileName, String contentType,
            byte[] content) {
        Objects.requireNonNull(content, "content must not be null");
        StorageObjectDescriptor document = ObjectKeys.describe(
                bucket, tenantId, category, referenceId, fileName, contentType, StorageMode.STORED);
        ensureBucket();
        try {
            s3.putObject(put -> put
                            .bucket(bucket)
                            .key(document.objectKey())
                            .contentType(contentType)
                            .metadata(Map.of("tenant-id", tenantId, "category", document.category())),
                    RequestBody.fromBytes(content));
        } catch (SdkException ex) {
            throw unavailable("store the document", ex);
        }
        return document;
    }

    @Override
    public URI downloadUrl(StorageObjectDescriptor document, Duration validFor) {
        requireBucket(document);
        try {
            return presigner.presignGetObject(presign -> presign
                            .signatureDuration(validFor)
                            .getObjectRequest(get -> get.bucket(bucket).key(document.objectKey())))
                    .url()
                    .toURI();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Presigned URL is not a valid URI", e);
        }
    }

    @Override
    public URI uploadUrl(StorageObjectDescriptor document, Duration validFor) {
        requireBucket(document);
        // The client's PUT cannot create the bucket: it has to be there before the first link.
        ensureBucket();
        try {
            return presigner.presignPutObject(presign -> presign
                            .signatureDuration(validFor)
                            .putObjectRequest(put -> put
                                    .bucket(bucket)
                                    .key(document.objectKey())
                                    .contentType(document.contentType())
                                    // Write once: the store refuses the PUT if the key exists.
                                    .ifNoneMatch("*")))
                    .url()
                    .toURI();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Presigned URL is not a valid URI", e);
        }
    }

    @Override
    public boolean exists(StorageObjectDescriptor document) {
        requireBucket(document);
        try {
            s3.headObject(head -> head.bucket(bucket).key(document.objectKey()));
            return true;
        } catch (S3Exception ex) {
            // 404: no such key, or the bucket was never created (nothing uploaded yet)
            if (ex.statusCode() == 404) {
                return false;
            }
            throw unavailable("look the document up", ex);
        } catch (SdkException ex) {
            throw unavailable("look the document up", ex);
        }
    }

    private void requireBucket(StorageObjectDescriptor document) {
        if (!bucket.equals(document.bucketName())) {
            throw new IllegalArgumentException("Document is not in bucket " + bucket);
        }
    }

    private void ensureBucket() {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            try {
                try {
                    s3.headBucket(head -> head.bucket(bucket));
                } catch (NoSuchBucketException e) {
                    createBucket();
                } catch (S3Exception e) {
                    if (e.statusCode() != 404) {
                        throw e;
                    }
                    createBucket();
                }
            } catch (SdkException ex) {
                throw unavailable("prepare bucket " + bucket, ex);
            }
            bucketReady = true;
        }
    }

    private static ObjectStoreUnavailableException unavailable(String action, SdkException cause) {
        return new ObjectStoreUnavailableException("The object store did not " + action, cause);
    }

    private void createBucket() {
        log.info("Creating bucket '{}'", bucket);
        s3.createBucket(create -> create.bucket(bucket));
    }

    @Override
    public void close() {
        presigner.close();
        s3.close();
    }
}
