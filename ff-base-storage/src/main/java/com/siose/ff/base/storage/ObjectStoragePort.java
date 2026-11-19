package com.siose.ff.base.storage;

import java.net.URI;
import java.time.Duration;

/**
 * Port for tenant-scoped document storage (e-CMR PDFs, proof-of-delivery photos and signatures,
 * inspection images, report exports).
 *
 * <p>The domain code depends only on this interface. Adapters:
 * <ul>
 *   <li>{@link ReferenceOnlyStorageAdapter}: computes object keys, stores nothing (default; tests).</li>
 *   <li>{@link S3ObjectStorageAdapter}: any S3-compatible store through the AWS SDK
 *       (RustFS in the local cluster; AWS S3, Cloudflare R2 or SeaweedFS elsewhere).</li>
 * </ul>
 *
 * <p>Layout: one bucket, one key prefix per tenant:
 * {@code <bucket>/<tenant>/<category>/<referenceId>/<fileName>}.
 */
public interface ObjectStoragePort {

    /**
     * Reserves the location of a document that will be stored later. Writes nothing.
     */
    StorageObjectDescriptor registerDocumentReference(
            String tenantId,
            String category,
            String referenceId,
            String fileName,
            String contentType
    );

    /**
     * Stores the document bytes under the tenant's prefix and returns where they are.
     *
     * @throws ObjectStoreUnavailableException if no object store is configured, or it does not answer
     */
    StorageObjectDescriptor store(
            String tenantId,
            String category,
            String referenceId,
            String fileName,
            String contentType,
            byte[] content
    );

    /**
     * A time-limited download link for a stored document, for browsers and other clients
     * that should not receive storage credentials.
     *
     * @throws ObjectStoreUnavailableException if no object store is configured, or it does not answer
     */
    URI downloadUrl(StorageObjectDescriptor document, Duration validFor);

    /**
     * A time-limited upload link. The client sends the bytes to this address with {@code PUT},
     * the descriptor's content type and {@code If-None-Match: *}; they do not pass through the
     * application. Where the store supports conditional writes, the second upload to the same key
     * is refused, so a link issued earlier cannot overwrite a document.
     *
     * @throws ObjectStoreUnavailableException if no object store is configured, or it does not answer
     */
    URI uploadUrl(StorageObjectDescriptor document, Duration validFor);

    /**
     * Whether the store already holds an object at this document's key.
     *
     * @throws ObjectStoreUnavailableException if no object store is configured, or it does not answer
     */
    boolean exists(StorageObjectDescriptor document);
}
