package com.siose.ff.base.storage;

import java.time.Instant;

/**
 * Where a tenant document lives (or will live) in object storage.
 *
 * @param objectUri provider-neutral location, {@code s3://<bucket>/<objectKey>}; safe to persist
 *                  because it does not contain an endpoint or credentials
 */
public record StorageObjectDescriptor(
        String tenantId,
        String bucketName,
        String objectKey,
        String category,
        String contentType,
        StorageMode storageMode,
        String objectUri,
        Instant registeredAt
) {

    /** Whether the bytes exist in the store yet. */
    public enum StorageMode {
        /** Location reserved; nothing uploaded. */
        REFERENCE_ONLY,
        /** Bytes uploaded to the object store. */
        STORED
    }
}
