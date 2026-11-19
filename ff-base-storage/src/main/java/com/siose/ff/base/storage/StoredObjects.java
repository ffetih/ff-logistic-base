package com.siose.ff.base.storage;

import com.siose.ff.base.storage.StorageObjectDescriptor.StorageMode;
import java.time.Instant;
import java.util.Objects;

/**
 * Reads a persisted {@code s3://<bucket>/<key>} location back into a descriptor.
 * The key is the one {@link ObjectKeys} wrote: {@code <tenant>/<category>/<referenceId>/<fileName>}.
 */
public final class StoredObjects {

    private StoredObjects() {
    }

    /**
     * @param contentType required on upload, because the presigned PUT signs that header
     */
    public static StorageObjectDescriptor fromObjectUri(String objectUri, String contentType) {
        Objects.requireNonNull(contentType, "contentType must not be null");
        if (objectUri == null || !objectUri.startsWith("s3://")) {
            throw new IllegalArgumentException("Document location must be an s3 URI");
        }
        String rest = objectUri.substring("s3://".length());
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash == rest.length() - 1) {
            throw new IllegalArgumentException("Document location must include a bucket and a key");
        }
        String bucket = rest.substring(0, slash);
        String key = rest.substring(slash + 1);
        String[] parts = key.split("/", -1);
        if (parts.length != 4 || isBlank(parts[0]) || isBlank(parts[1]) || isBlank(parts[2]) || isBlank(parts[3])) {
            throw new IllegalArgumentException("Document location is not a tenant object key");
        }
        return new StorageObjectDescriptor(
                parts[0],
                bucket,
                key,
                parts[1],
                contentType,
                StorageMode.REFERENCE_ONLY,
                objectUri,
                Instant.EPOCH
        );
    }

    /**
     * The descriptor's tenant is the key prefix, which is the sanitised tenant id.
     */
    public static boolean sameTenant(StorageObjectDescriptor document, String tenantId) {
        return ObjectKeys.segment(tenantId, "tenantId").equals(document.tenantId());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
