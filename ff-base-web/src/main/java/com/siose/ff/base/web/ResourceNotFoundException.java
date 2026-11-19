package com.siose.ff.base.web;

/** Thrown when a requested resource does not exist for the current tenant (HTTP 404). */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
