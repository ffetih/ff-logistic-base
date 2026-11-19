package com.siose.ff.base.storage;

import com.siose.ff.base.web.DependencyUnavailableException;

/**
 * The object store cannot serve the request: none is configured
 * ({@code ff.storage.provider=reference-only}), or the configured one did not answer.
 * 503 {@code object-store-unavailable}.
 */
public class ObjectStoreUnavailableException extends DependencyUnavailableException {

    public ObjectStoreUnavailableException(String message) {
        super(message);
    }

    public ObjectStoreUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String problemSlug() {
        return "object-store-unavailable";
    }

    @Override
    public String problemTitle() {
        return "Object Store Unavailable";
    }
}
