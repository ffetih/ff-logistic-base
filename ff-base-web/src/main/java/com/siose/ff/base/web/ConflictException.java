package com.siose.ff.base.web;

/**
 * The request clashes with data that already exists, and repeating it unchanged will not help:
 * for example an order number that is already taken by a different order. Answered with 409 and a
 * problem type of its own. Extend it instead of writing another exception handler.
 *
 * <p>Two requests that change the same record at the same moment are a different case: that is
 * {@code concurrent-update}, and repeating the request is safe.
 */
public abstract class ConflictException extends RuntimeException {

    protected ConflictException(String message) {
        super(message);
    }

    /** Last part of the problem type, for example {@code customer-order-conflict}. */
    public abstract String problemSlug();

    /** Short title of the problem, for example {@code Customer Order Conflict}. */
    public abstract String problemTitle();
}
