package com.siose.ff.base.storage;

import com.siose.ff.base.storage.StorageObjectDescriptor.StorageMode;
import java.net.URI;
import java.time.Duration;

/**
 * Default adapter ({@code ff.storage.provider=reference-only}): reserves object locations
 * with the same key layout as the S3 adapter, but has no store behind it.
 */
public class ReferenceOnlyStorageAdapter implements ObjectStoragePort {

    private final String bucket;

    public ReferenceOnlyStorageAdapter(String bucket) {
        this.bucket = ObjectKeys.segment(bucket, "bucket");
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
        throw notConfigured();
    }

    @Override
    public URI downloadUrl(StorageObjectDescriptor document, Duration validFor) {
        throw notConfigured();
    }

    @Override
    public URI uploadUrl(StorageObjectDescriptor document, Duration validFor) {
        throw notConfigured();
    }

    @Override
    public boolean exists(StorageObjectDescriptor document) {
        throw notConfigured();
    }

    private static ObjectStoreUnavailableException notConfigured() {
        return new ObjectStoreUnavailableException(
                "No object store configured (ff.storage.provider=reference-only). "
                        + "Set it to s3 to store documents.");
    }
}
