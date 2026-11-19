package com.siose.ff.base.storage;

import com.siose.ff.base.web.ConflictException;

/**
 * An upload was asked for, but the store already holds the document. Documents are written once:
 * a signed e-CMR or proof of delivery must not be replaced through the API. 409
 * {@code document-already-stored}.
 */
public class DocumentAlreadyStoredException extends ConflictException {

    public DocumentAlreadyStoredException(String message) {
        super(message);
    }

    @Override
    public String problemSlug() {
        return "document-already-stored";
    }

    @Override
    public String problemTitle() {
        return "Document Already Stored";
    }
}
