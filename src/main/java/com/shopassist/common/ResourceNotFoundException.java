package com.shopassist.common;

/**
 * The requested resource does not exist, or does not belong to the caller.
 *
 * <p>One exception for both cases by design. If "not yours" produced a different
 * status from "no such thing", an attacker could walk the order-number space and
 * learn which numbers are real. Callers see an identical 404 either way.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
