package com.siose.ff.base.storage;

import com.siose.ff.base.web.ResourceNotFoundException;

/** A download link was asked for, but nothing has been uploaded yet: 404. */
public class DocumentNotStoredException extends ResourceNotFoundException {

    public DocumentNotStoredException(String message) {
        super(message);
    }
}
