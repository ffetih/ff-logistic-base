package com.siose.ff.base.web;

/**
 * Something the request needs from outside is not available: another system did not answer, or a
 * required setting is missing. Answered with 503 and a problem type of its own, so a client can
 * tell which dependency failed. Extend it instead of writing another exception handler.
 */
public abstract class DependencyUnavailableException extends RuntimeException {

    protected DependencyUnavailableException(String message) {
        super(message);
    }

    protected DependencyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Last part of the problem type, for example {@code road-matrix-unavailable}. */
    public abstract String problemSlug();

    /** Short title of the problem, for example {@code Road Matrix Unavailable}. */
    public abstract String problemTitle();
}
