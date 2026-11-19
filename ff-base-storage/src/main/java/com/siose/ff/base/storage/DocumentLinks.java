package com.siose.ff.base.storage;

import com.siose.ff.base.tenant.TenantAccessException;
import java.time.Duration;
import java.util.Map;

/**
 * Turns a document location already stored on an aggregate into an upload or download address.
 * The bytes go between the client and the object store.
 *
 * <p>Before signing, the location must lie under the caller's tenant prefix; otherwise
 * {@link TenantAccessException} (403). The aggregate was already loaded for that tenant, so this
 * is a second line of defence against a wrong or tampered location.
 *
 * <p>Documents are written once. An upload link is refused with
 * {@link DocumentAlreadyStoredException} (409) when the store already holds the document, and the
 * link itself requires {@code If-None-Match: *}, so a link issued earlier cannot overwrite it
 * either, on stores that support conditional writes. A download link is refused with
 * {@link DocumentNotStoredException} (404) while nothing has been uploaded: the store would only
 * answer the client with an XML error of its own.
 */
public final class DocumentLinks {

    public static final Duration VALID_FOR = Duration.ofMinutes(15);

    private DocumentLinks() {
    }

    public static DocumentTransfer upload(
            ObjectStoragePort storage, String tenantId, String objectUri, String contentType) {
        StorageObjectDescriptor document = owned(tenantId, objectUri, contentType);
        if (storage.exists(document)) {
            throw new DocumentAlreadyStoredException("This document has already been uploaded and cannot be replaced");
        }
        return new DocumentTransfer(storage.uploadUrl(document, VALID_FOR), "PUT", VALID_FOR.toSeconds(),
                contentType, Map.of("Content-Type", contentType, "If-None-Match", "*"));
    }

    public static DocumentTransfer download(
            ObjectStoragePort storage, String tenantId, String objectUri, String contentType) {
        StorageObjectDescriptor document = owned(tenantId, objectUri, contentType);
        if (!storage.exists(document)) {
            throw new DocumentNotStoredException("This document has not been uploaded yet");
        }
        return new DocumentTransfer(storage.downloadUrl(document, VALID_FOR), "GET", VALID_FOR.toSeconds(),
                contentType, Map.of());
    }

    private static StorageObjectDescriptor owned(String tenantId, String objectUri, String contentType) {
        StorageObjectDescriptor document = StoredObjects.fromObjectUri(objectUri, contentType);
        if (!StoredObjects.sameTenant(document, tenantId)) {
            throw new TenantAccessException("Document is not in this tenant's prefix");
        }
        return document;
    }
}
